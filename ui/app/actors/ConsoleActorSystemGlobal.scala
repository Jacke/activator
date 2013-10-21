/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package actors

import akka.actor.{ Props, ActorSystem }
import scala.concurrent.duration._

object ConsoleActorSystemGlobal {
  private val system = ActorSystem("ConsoleClientActorSystem")
  val clientHandler = system.actorOf(Props[ClientActorController], Actors.clientName)
  val scheduleFrequency = system.settings.config.getInt("console.update-frequency").seconds
}
