package test

import org.junit.Assert._
import org.junit._
import snap.RootConfig
import snap.AppConfig
import java.io.File
import scala.concurrent._
import scala.concurrent.duration._
import activator.properties.ActivatorProperties.ACTIVATOR_USER_HOME
import java.io.FileOutputStream

// these tests are all synchronized because they are testing
// behavior of global state (RootConfig.user).
class ConfigTest {

  @Before
  def beforeEachTest(): Unit = {
    val d = new File(ACTIVATOR_USER_HOME())
    d.mkdirs()

    if (!d.exists() || !d.isDirectory())
      throw new Exception("failed to create " + d)
  }

  @Test
  def testUserConfig(): Unit = synchronized {
    val rewritten = RootConfig.rewriteUser { old =>
      val appList = if (old.applications.exists(_.location.getPath == "foo"))
        old.applications
      else
        AppConfig(new File("foo"), "id") +: old.applications
      old.copy(applications = appList)
    }
    Await.ready(rewritten, 5.seconds)
    val c = RootConfig.user
    assertTrue("app 'foo' now in user config", c.applications.exists(_.location.getPath == "foo"))
  }

  def removeProjectName(): Unit = {
    val rewritten = RootConfig.rewriteUser { old =>
      val withNoName = old.applications
        .find(_.location.getPath == "foo")
        .getOrElse(AppConfig(new File("foo"), "id"))
        .copy(cachedName = None)

      val appList = withNoName +: old.applications.filter(_.location.getPath != "foo")
      old.copy(applications = appList)
    }
    Await.ready(rewritten, 5.seconds)
    val c = RootConfig.user
    assertTrue("app 'foo' now in user config with no name",
      c.applications.exists({ p => p.location.getPath == "foo" && p.cachedName.isEmpty }))
  }

  @Test
  def testAddingProjectName(): Unit = synchronized {
    removeProjectName()

    val rewritten = RootConfig.rewriteUser { old =>
      val withName = old.applications
        .find(_.location.getPath == "foo")
        .getOrElse(AppConfig(new File("foo"), "id"))
        .copy(cachedName = Some("Hello World"))

      val appList = withName +: old.applications.filter(_.location.getPath != "foo")
      old.copy(applications = appList)
    }
    Await.ready(rewritten, 5.seconds)
    val c = RootConfig.user
    assertTrue("app 'foo' now in user config with a name",
      c.applications.exists({ p => p.location.getPath == "foo" && p.cachedName == Some("Hello World") }))
  }

  @Test
  def testRecoveringFromBrokenFile(): Unit = synchronized {
    val file = new File(ACTIVATOR_USER_HOME(), "config.json")
    try {
      file.delete()

      val stream = new FileOutputStream(file)
      stream.write("{ invalid json! ]".getBytes())
      stream.close()

      RootConfig.forceReload()

      val e = try {
        RootConfig.user
        throw new AssertionError("We expected to get an exception and not reach here (first time)")
      } catch {
        case e: Exception => e
      }

      assertTrue("got the expected exception on bad json", e.getMessage().contains("was expecting double"))

      // bad json is still there, so things should still fail...
      val e2 = try {
        RootConfig.user
        throw new AssertionError("We expected to get an exception and not reach here (second time)")
      } catch {
        case e: Exception => e
      }

      assertTrue("got the expected exception on bad json", e2.getMessage().contains("was expecting double"))

      // delete the file... should now load the file fine
      if (!file.delete())
        throw new AssertionError("failed to delete " + file.getAbsolutePath())

      try {
        assertTrue("loaded an empty config after recovering from corrupt one", RootConfig.user.applications.isEmpty)
      } catch {
        case e: Exception =>
          throw new AssertionError("should not have had an error loading empty config", e)
      }
    } finally {
      // to avoid weird failures on next run of the tests
      file.delete()
    }
  }

  @Test
  def testRecoveringFromBrokenFileManyTimes(): Unit = synchronized {
    // this is intended to reveal a race that we were seeing intermittently
    for (_ <- 1 to 100)
      testRecoveringFromBrokenFile()
  }
}
