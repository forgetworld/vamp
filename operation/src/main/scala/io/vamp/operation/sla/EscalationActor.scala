package io.vamp.operation.sla

import java.time.OffsetDateTime

import akka.actor._
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.model.artifact.DeploymentService.State.Step.Initiated
import io.vamp.model.artifact._
import io.vamp.model.event.{ EventQuery, TimeRange }
import io.vamp.model.notification.{ DeEscalate, Escalate, SlaEvent }
import io.vamp.model.reader.MegaByte
import io.vamp.operation.notification.{ InternalServerError, OperationNotificationProvider, UnsupportedEscalationType }
import io.vamp.operation.sla.EscalationActor.EscalationProcessAll
import io.vamp.persistence.db.{ ArtifactPaginationSupport, EventPaginationSupport, PersistenceActor }
import io.vamp.pulse.PulseActor

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

class EscalationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  private var windowStart: Option[OffsetDateTime] = None

  def tick() = windowStart match {
    case Some(from) ⇒
      val to = OffsetDateTime.now().withNano(0)
      actorFor[EscalationActor] ! EscalationProcessAll(from, to)
      windowStart = Some(to)

    case None ⇒
  }

  override def schedule(period: FiniteDuration, initialDelay: FiniteDuration) = {
    period.toNanos match {
      case interval if interval > 0 ⇒ if (windowStart.isEmpty) windowStart = Some(OffsetDateTime.now().withNano(0))
      case _                        ⇒ windowStart = None
    }
    super.schedule(period, initialDelay)
  }

}

object EscalationActor {

  case class EscalationProcessAll(from: OffsetDateTime, to: OffsetDateTime)

}

class EscalationActor extends ArtifactPaginationSupport with EventPaginationSupport with CommonSupportForActors with OperationNotificationProvider {

  def tags = Set("escalation")

  def receive: Receive = {
    case EscalationProcessAll(from, to) ⇒
      implicit val timeout = PersistenceActor.timeout
      allArtifacts[Deployment] map (check(_, from, to))
  }

  private def check(deployments: List[Deployment], from: OffsetDateTime, to: OffsetDateTime) = {
    deployments.foreach(deployment ⇒ {
      try {
        deployment.clusters.foreach(cluster ⇒ cluster.sla match {
          case None ⇒
          case Some(sla) ⇒
            sla.escalations.foreach { _ ⇒
              querySlaEvents(deployment, cluster, from, to) map {
                case escalationEvents ⇒ escalationEvents.foreach {
                  case Escalate(d, c, _) if d.name == deployment.name && c.name == cluster.name ⇒ escalateToAll(deployment, cluster, sla.escalations, escalate = true)
                  case DeEscalate(d, c, _) if d.name == deployment.name && c.name == cluster.name ⇒ escalateToAll(deployment, cluster, sla.escalations, escalate = false)
                  case _ ⇒
                }
              }
            }
        })
      } catch {
        case any: Throwable ⇒ reportException(InternalServerError(any))
      }
    })
  }

  private def querySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime): Future[List[SlaEvent]] = {
    implicit val timeout = PulseActor.timeout
    val eventQuery = EventQuery(SlaEvent.slaTags(deployment, cluster), Some(TimeRange(Some(from), Some(to), includeLower = false, includeUpper = true)))

    allEvents(eventQuery) map {
      case events ⇒
        events.flatMap { event ⇒
          if (Escalate.tags.forall(event.tags.contains))
            Escalate(deployment, cluster, event.timestamp) :: Nil
          else if (DeEscalate.tags.forall(event.tags.contains))
            DeEscalate(deployment, cluster, event.timestamp) :: Nil
          else
            Nil
        }
    }
  }

  private def escalateToAll(deployment: Deployment, cluster: DeploymentCluster, escalations: List[Escalation], escalate: Boolean): Boolean = {
    escalations.foldLeft(false)((result, escalation) ⇒ result || (escalation match {
      case e: ScaleEscalation[_] ⇒
        log.debug(s"scale escalation: ${deployment.name}/${cluster.name}")
        scaleEscalation(deployment, cluster, e, escalate)

      case e: ToAllEscalation ⇒
        log.debug(s"to all escalation: ${deployment.name}/${cluster.name}")
        escalateToAll(deployment, cluster, e.escalations, escalate)

      case e: ToOneEscalation ⇒
        log.debug(s"to one escalation: ${deployment.name}/${cluster.name}")
        (if (escalate) e.escalations else e.escalations.reverse).find(escalation ⇒ escalateToAll(deployment, cluster, escalation :: Nil, escalate)) match {
          case None    ⇒ false
          case Some(_) ⇒ true
        }

      case e: GenericEscalation ⇒
        info(UnsupportedEscalationType(e.`type`))
        false

      case e: Escalation ⇒
        throwException(UnsupportedEscalationType(e.name))
        false
    }))
  }

  private def scaleEscalation(deployment: Deployment, cluster: DeploymentCluster, escalation: ScaleEscalation[_], escalate: Boolean): Boolean = {
    def commit(targetCluster: DeploymentCluster, scale: DefaultScale) = {
      // Scale only the first service.
      actorFor[PersistenceActor] ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(c ⇒ {
        if (c.name == targetCluster.name)
          c.copy(services = c.services match {
            case head :: tail ⇒ head.copy(scale = Some(scale), state = head.state.copy(step = Initiated())) :: tail
            case Nil          ⇒ Nil
          })
        else
          c
      })))
    }

    (escalation.targetCluster match {
      case None ⇒ Some(cluster)
      case Some(name) ⇒ deployment.clusters.find(_.name == name) match {
        case None    ⇒ None
        case Some(c) ⇒ Some(c)
      }
    }) match {
      case None ⇒ false
      case Some(targetCluster) ⇒
        // Scale only the first service.
        val scale = targetCluster.services.head.scale.get
        escalation match {

          case ScaleInstancesEscalation(_, minimum, maximum, scaleBy, _) ⇒
            val instances = if (escalate) scale.instances + scaleBy else scale.instances - scaleBy
            if (instances <= maximum && instances >= minimum) {
              log.info(s"scale instances: ${deployment.name}/${targetCluster.name} to $instances")
              commit(targetCluster, scale.copy(instances = instances.toInt))
              true
            } else {
              log.debug(s"scale instances not within boundaries: ${deployment.name}/${targetCluster.name} is already ${scale.instances}")
              false
            }

          case ScaleCpuEscalation(_, minimum, maximum, scaleBy, _) ⇒
            val cpu = if (escalate) scale.cpu + scaleBy else scale.cpu - scaleBy
            if (cpu <= maximum && cpu >= minimum) {
              log.info(s"scale cpu: ${deployment.name}/${targetCluster.name} to $cpu")
              commit(targetCluster, scale.copy(cpu = cpu))
              true
            } else {
              log.debug(s"scale cpu not within boundaries: ${deployment.name}/${targetCluster.name} is already ${scale.cpu}")
              false
            }

          case ScaleMemoryEscalation(_, minimum, maximum, scaleBy, _) ⇒
            val memory = if (escalate) scale.memory.value + scaleBy else scale.memory.value - scaleBy
            if (memory <= maximum && memory >= minimum) {
              log.info(s"scale memory: ${deployment.name}/${targetCluster.name} to $memory")
              commit(targetCluster, scale.copy(memory = MegaByte(memory)))
              true
            } else {
              log.debug(s"scale memory not within boundaries: ${deployment.name}/${targetCluster.name} is already ${scale.memory}")
              false
            }
        }
    }
  }
}

