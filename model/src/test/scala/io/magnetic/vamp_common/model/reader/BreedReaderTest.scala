package io.magnetic.vamp_common.model.reader

import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.model.notification._
import io.magnetic.vamp_core.model.reader.BreedReader
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BreedReaderTest extends ReaderTest {

  "BreedReader" should "read the simplest YAML (name/deployable only)" in {
    BreedReader.read(res("breed1.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the ports" in {
    BreedReader.read(res("breed2.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(HttpPort("port", None, Some(8080), Trait.Direction.Out))),
      'ports(List(HttpPort("port", None, Some(8080), Trait.Direction.Out))),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the environment variables and dependencies" in {
    BreedReader.read(res("breed3.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(HttpPort("port", None, Some(8080), Trait.Direction.Out), EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In), EnvironmentVariable("db.ports.port", Some("DB_PORT"), None, Trait.Direction.In))),
      'ports(List(HttpPort("port", None, Some(8080), Trait.Direction.Out))),
      'environmentVariables(List(EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In), EnvironmentVariable("db.ports.port", Some("DB_PORT"), None, Trait.Direction.In))),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with value expansion" in {
    BreedReader.read(res("breed4.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(HttpPort("port", None, Some(8080), Trait.Direction.Out), EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In))),
      'ports(List(HttpPort("port", None, Some(8080), Trait.Direction.Out))),
      'environmentVariables(List(EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In))),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with partially expanded reference dependencies" in {
    BreedReader.read(res("breed5.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with fully expanded reference dependencies" in {
    BreedReader.read(res("breed6.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> BreedReference("mysql")))
    )
  }

  it should "read the YAML source with embedded dependencies" in {
    BreedReader.read(res("breed7.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> DefaultBreed("mysql", Deployable("magneticio/mysql:latest"), List(), List(), Map())))
    )
  }

  it should "read the YAML source with expanded embedded dependencies" in {
    BreedReader.read(res("breed8.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List()),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" -> DefaultBreed("mysql", Deployable("magneticio/mysql:latest"), List(), List(), Map())))
    )
  }

  it should "read the YAML source with embedded dependencies with dependencies" in {
    BreedReader.read(res("breed9.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'traits(List(HttpPort("port", None, Some(8080), Trait.Direction.Out), EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In), EnvironmentVariable("db.ports.port", Some("DB_PORT"), None, Trait.Direction.In))),
      'ports(List(HttpPort("port", None, Some(8080), Trait.Direction.Out))),
      'environmentVariables(List(EnvironmentVariable("db.host", Some("DB_HOST"), None, Trait.Direction.In), EnvironmentVariable("db.ports.port", Some("DB_PORT"), None, Trait.Direction.In))),
      'dependencies(Map("db" -> DefaultBreed("mysql-wrapper", Deployable("magneticio/mysql-wrapper:latest"), List(TcpPort("port", None, Some(3006), Trait.Direction.Out)), List(), Map("mysql" -> BreedReference("mysql")))))
    )
  }

  it should "fail on no deployable" in {
    expectedError[MissingPathValueError]({
      BreedReader.read(res("breed10.yml"))
    }) should have(
      'path("deployable")
    )
  }

  it should "fail on missing port values" in {
    expectedError[MissingPortValueError]({
      BreedReader.read(res("breed11.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(TcpPort("port", None, None, Trait.Direction.Out)), List(), Map())),
      'port(TcpPort("port", None, None, Trait.Direction.Out))
    )
  }

  it should "fail on missing environment variable values" in {
    expectedError[MissingEnvironmentVariableValueError]({
      BreedReader.read(res("breed12.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(), List(EnvironmentVariable("port", None, None, Trait.Direction.Out)), Map())),
      'environmentVariable(EnvironmentVariable("port", None, None, Trait.Direction.Out))
    )
  }

  it should "fail on non unique port name" in {
    expectedError[NonUniquePortNameError]({
      BreedReader.read(res("breed13.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(HttpPort("port", None, Some(80), Trait.Direction.Out), HttpPort("port", None, Some(8080), Trait.Direction.Out)), List(), Map())),
      'port(HttpPort("port", None, Some(80), Trait.Direction.Out))
    )
  }

  it should "fail on non unique environment variable name" in {
    expectedError[NonUniqueEnvironmentVariableNameError]({
      BreedReader.read(res("breed14.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(), List(EnvironmentVariable("port", None, Some("80/http"), Trait.Direction.Out), EnvironmentVariable("port", None, Some("8080/http"), Trait.Direction.Out)), Map())),
      'environmentVariable(EnvironmentVariable("port", None, Some("80/http"), Trait.Direction.Out))
    )
  }

  it should "fail on unresolved dependency reference" in {
    expectedError[UnresolvedDependencyForTraitError]({
      BreedReader.read(res("breed15.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(), List(EnvironmentVariable("es.ports.port", None, None, Trait.Direction.In)), Map("db" -> BreedReference("mysql")))),
      'name(Trait.Name.asName("es.ports.port"))
    )
  }

  it should "fail on missing dependency environment variable" in {
    expectedError[UnresolvedDependencyForTraitError]({
      BreedReader.read(res("breed16.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(), List(EnvironmentVariable("db.ports.web", None, None, Trait.Direction.In)), Map("db" -> DefaultBreed("mysql", Deployable("vamp/mysql"), List(), List(), Map())))),
      'name(Trait.Name.asName("db.ports.web"))
    )
  }

  it should "fail on missing dependency port" in {
    expectedError[UnresolvedDependencyForTraitError]({
      BreedReader.read(res("breed17.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(TcpPort("db.ports.web", None, None, Trait.Direction.In)), List(), Map("db" -> DefaultBreed("mysql", Deployable("vamp/mysql"), List(), List(), Map())))),
      'name(Trait.Name.asName("db.ports.web"))
    )
  }

  it should "fail on direct recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BreedReader.read(res("breed18.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Deployable("magneticio/monarch:latest"), List(TcpPort("db.ports.web", None, None, Trait.Direction.In)), List(), Map("db" -> BreedReference("monarch"))))
    )
  }

  it should "fail on indirect recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BreedReader.read(res("breed19.yml"))
    }) should have(
      'breed(DefaultBreed("monarch2", Deployable("magneticio/monarch2:latest"), List(), List(), Map("es" -> BreedReference("monarch1"))))
    )
  }
}
