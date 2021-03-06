package factorio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AssemblerSpec extends AnyFlatSpec with Matchers {

  @blueprint
  trait ServiceBlueprint {

    val serviceBinder = bind[Service].to[ServiceImpl]
    val otherServiceBinder = bind[OtherService].to[OtherServiceImpl]

  }

  "Assembly macro" should "assemble a component" in {

    val assembler = Assembler[Repository](Blank)

    assembler()
    succeed
  }

  it should "assemble a simple app" in {

    val assembler = Assembler[App](new ServiceBlueprint {})

    val app = assembler()
    app.service.repository shouldBe app.otherService.repository
  }

  it should "assemble an app from a blueprint with a simple @provides method" in {

    val database = new Database

    @blueprint
    class AppBlueprint extends ServiceBlueprint {

      @provides
      def getDatabase: Database =
        database
    }

    val assembler = Assembler[App](new AppBlueprint)

    val app = assembler()

    app.service.repository.database shouldBe database
    app.otherService.repository.database shouldBe database
  }

  it should "assemble an app from a blueprint with a complex @provides method" in {

    val database = new Database
    val repository = new Repository(database)
    val service = new ServiceImpl(repository)

    @blueprint
    class AppBlueprint extends ServiceBlueprint {

      @provides
      def createApp(otherService: OtherService): App =
        new App(service, otherService)
    }

    val assembler = Assembler[App](new AppBlueprint)

    val app = assembler()

    app.service shouldBe service
    app.otherService.repository should not be (repository)
  }

  it should "assemble an app from a blueprint with named components" in {

    val database = new Database
    val otherDatabase = new Database

    @blueprint
    class MultiDatabaseRepositoryBlueprint {

      @provides
      @named("database")
      def getDatabase =
        database

      @provides
      @named("otherDatabase")
      def getOtherDatabase =
        otherDatabase
    }

    val assembler = Assembler[MultiDatabaseRepository](new MultiDatabaseRepositoryBlueprint)

    val repository = assembler()

    repository.database shouldBe database
    repository.otherDatabase shouldBe otherDatabase
  }

  it should "assemble an app from a blueprint with named, binded components" in {

    val thatClient: Client = new ThatClientImpl

    @blueprint
    class ServicesBlueprint {

      @provides
      @named("that")
      def getThatClient =
        thatClient

      @named("other")
      val otherClientBinder = bind[Client].to[OtherClientImpl]
    }

    val assembler = Assembler[Clients](new ServicesBlueprint)

    val clients = assembler()

    clients.thatClient shouldBe thatClient
    clients.otherClient should not be (thatClient)
    clients.otherClient.getClass shouldBe classOf[OtherClientImpl]
  }

  it should "assemble an app from a blueprint with replicated components" in {

    @blueprint
    class ReplicatedServiceBlueprint extends ServiceBlueprint {
      val bindRepository = bind[Repository].to[ReplicatedRepository]
    }

    val assembler = Assembler[App](new ReplicatedServiceBlueprint)

    val app = assembler()

    app.service.repository shouldNot be(app.otherService.repository)
    app.service.repository.database shouldBe app.otherService.repository.database
  }

  it should "assemble an app from a blueprint with replicated, provided component" in {

    @blueprint
    class ReplicatedRepositoryBlueprint extends ServiceBlueprint {

      @provides
      @replicated
      def newRepository(database: Database) =
        new Repository(database)
    }

    val assembler = Assembler[App](new ReplicatedRepositoryBlueprint)

    val app = assembler()

    app.service.repository shouldNot be(app.otherService.repository)
    app.service.repository.database shouldBe app.otherService.repository.database
  }

  it should "assemble an app from a blueprint with multiple composed annotations" in {

    @blueprint
    class AppBlueprint extends ServiceBlueprint {

      @provides
      @replicated
      def getDatabase: Database =
        new Database
    }

    val assembler = Assembler[App](new AppBlueprint)

    val app = assembler()

    app.service.repository.database should not be (app.otherService.repository)
  }

  it should "honor `@overrides` annotation when analyzing binders" in {

    @blueprint
    trait MemberBlueprint {
      @overrides
      private val memberBinder = bind[Member].to[MemberImpl]
    }

    @blueprint
    trait OtherMemberBlueprint {
      //@overrides
      private val memberBinder = bind[Member].to[OtherMemberImpl]
    }

    val assembler = Assembler[Package](new MemberBlueprint with OtherMemberBlueprint {})

    val instance: Package = assembler()

    instance.member.getClass shouldBe classOf[MemberImpl]
  }

  it should "warn about duplicated providers" in {

    @blueprint
    trait MemberBlueprint {

      @provides
      @overrides
      def createMember: Member =
        new MemberImpl
    }

    @blueprint
    trait OtherMemberBlueprint {

      @provides
      def createOtherMember: Member =
        new OtherMemberImpl
    }

    val assembler = Assembler[Package](new MemberBlueprint with OtherMemberBlueprint {})

    val instance = assembler()

    instance.member.getClass() shouldBe classOf[MemberImpl]
  }

  it should "assemble an app from a blueprint class" in {

    val assembler = Assembler[App](new AppBlueprint)

    val app = assembler()

    app.service.getClass shouldBe classOf[ServiceImpl]
    app.otherService.getClass shouldBe classOf[OtherServiceImpl]
  }

  it should "reuse a more specific class in a binding" in {

    @blueprint
    class SingleServiceAppBlueprint {

      val serviceBinder =
        bind[Service].to[ServiceImpl]

    }

    val assembler = Assembler[SingleServiceApp](new SingleServiceAppBlueprint)

    val app = assembler()

    app.service shouldBe app.serviceImpl

  }

  it should "replicate a more specific class in a binding" in {

    @blueprint
    class SingleServiceAppBlueprint {

      @replicated
      val serviceBinder =
        bind[Service].to[ServiceImpl]

    }

    val assembler = Assembler[SingleServiceApp](new SingleServiceAppBlueprint)

    val app = assembler()

    app.service should not be (app.serviceImpl)

  }

  it should "find a `@named` provider with a stable identifier" in {

    @blueprint
    class StableIdentifierAppBlueprint {

      @provides
      //@named(StableIdentifierApp.thatClient)
      @named(StableIdentifierApp.thatClient)
      def thatClient: Client =
        new ThatClientImpl

    }

    val assembler = Assembler[StableIdentifierApp](new StableIdentifierAppBlueprint)

    val app = assembler()

    app.client.getClass() shouldBe classOf[ThatClientImpl]

  }

  it should "find a `@javax.Inject.Named` provider with a stable identifier" in {

    @blueprint
    class StableIdentifierAppBlueprint {

      @provides
      //@named(StableIdentifierApp.thatClient)
      @javax.inject.Named(StableIdentifierApp.thatClient)
      def thatClient: Client =
        new ThatClientImpl

    }

    val assembler = Assembler[StableIdentifierApp](new StableIdentifierAppBlueprint)

    val app = assembler()

    app.client.getClass() shouldBe classOf[ThatClientImpl]

  }

  it should "assembler an app with alias type provider" in {

    @blueprint
    class BoxedAppBlueprint {

      @provides
      def serviceBox(repo: Repository): BoxedApp.ServiceBox =
        new Box(new ServiceImpl(repo))

    }

    val assembler = Assembler[BoxedApp](new BoxedAppBlueprint)

    val app = assembler()

    app.serviceBox.value.getClass() shouldBe classOf[ServiceImpl]

  }

  it should "provide a component from annotated val" in {

    sealed trait Bar
    case object Bar extends Bar

    case class Foo(bar: Bar)

    @blueprint
    class AppBlueprint(@provides val bar: Bar)

    case class App(bar: Bar)

    val assembler = Assembler[App](new AppBlueprint(Bar))

    assembler().bar shouldBe Bar
  }

  it should "provide vals from deconstructed case class" in {

    sealed trait Bar
    case object Bar extends Bar

    sealed trait Baz
    case object Baz extends Baz

    case class Foo(bar: Bar, baz: Baz)

    @blueprint
    class AppBlueprint(@provides val foo: Foo) {
      @provides
      val Foo(bar, baz) = foo
    }

    case class App(foo: Foo, bar: Bar, baz: Baz)

    val assembler = Assembler[App](new AppBlueprint(Foo(Bar, Baz)))
    val app = assembler()

    app.bar shouldBe Bar
    app.baz shouldBe Baz
    app.foo shouldBe Foo(Bar, Baz)
  }

  it should "assembler an app with type syntax binder" in {

    @blueprint
    class AppBlueprint {

      val a: Service to ServiceImpl = bind[Service].to
      val b: OtherService to OtherServiceImpl = binder

    }

    val assembler = Assembler[App](new AppBlueprint)

    val app = assembler()

    app.service.getClass() shouldBe classOf[ServiceImpl]
    app.otherService.getClass() shouldBe classOf[OtherServiceImpl]

  }

  it should "not compile when circular dependency exists" in {
    // Assembler[CircularDependency](Blank)
    assertDoesNotCompile("Assembler[CircularDependency](Blank)")
  }

  it should "not compile when no binding was provided for an interface" in {
    // Assembler[App](Blank)
    assertDoesNotCompile("Assembler[App](Blank)")
  }
}
