package io.vamp.cli.commandline

import io.vamp.cli.commands._

trait CommandLineBasics {

  import ConsoleHelper._

  def terminateWithError[A](msg: String, returnValue: A = None): A = {
    println(s"ERROR: ".red.bold + "" + s"$msg".red)
    sys.exit(1)
    returnValue
  }

  def string2Command(s: String): CliCommand = s match {
    case "deploy"   ⇒ DeployCommand()
    case "info"     ⇒ InfoCommand()
    case "help"     ⇒ HelpCommand()
    case "--help"   ⇒ HelpCommand()
    case "version"  ⇒ VersionCommand()
    case "merge"    ⇒ MergeCommand()
    case "inspect"  ⇒ InspectCommand()
    case "list"     ⇒ ListCommand()
    case "generate" ⇒ GenerateCommand()
    case "create"   ⇒ CreateCommand()
    case "remove"   ⇒ RemoveCommand()
    case "undeploy" ⇒ UndeployCommand()
    case "update"   ⇒ UpdateCommand()
    case c          ⇒ UnknownCommand(c)
  }

  val appName = "vamp"

  def showHelp(command: CliCommand): Unit = {
    command match {
      case _: HelpCommand ⇒ {
        println(s"Usage: ".bold + "" + s"$appName COMMAND [args..]")
        println("")
        println("Commands:")
        showGeneralUsage(CreateCommand())
        showGeneralUsage(DeployCommand())
        showGeneralUsage(HelpCommand())
        showGeneralUsage(GenerateCommand())
        showGeneralUsage(InfoCommand())
        showGeneralUsage(InspectCommand())
        showGeneralUsage(ListCommand())
        showGeneralUsage(MergeCommand())
        showGeneralUsage(RemoveCommand())
        showGeneralUsage(UndeployCommand())
        showGeneralUsage(UpdateCommand())
        showGeneralUsage(VersionCommand())
        println("".reset)
        println(s"Run " + s"$appName COMMMAND --help".bold + "" + "  for additional help about the different command options")
      }

      case _ ⇒ {
        if (command.allowedArtifacts.isEmpty) {
          println(s"Usage: ".bold + "" + s"$appName ${command.name} ${if (command.requiresName) "NAME " else ""}${if (command.additionalParams.nonEmpty) command.additionalParams else ""} ")
        } else {
          println(s"Usage: ".bold + "" + s"$appName ${command.name} ${command.allowedArtifacts.mkString("|")} ${if (command.requiresName) "NAME " else ""}${if (command.additionalParams.nonEmpty) command.additionalParams else ""} ")
        }

        if (command.usage.nonEmpty) {
          println("")
          println(command.usage)
        }
        if (command.parameters.nonEmpty) {
          println("Parameters:")
          println(command.parameters)
        }
      }
    }
    sys.exit(0)
  }

  private def showGeneralUsage(command: CliCommand): Unit = {
    println(s"  ${command.name.padTo(20, ' ')}".bold + "" + s"${command.description}".yellow + "")
  }

}

