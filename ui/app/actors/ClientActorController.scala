/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package actors

import akka.actor._
import scala.concurrent.{ Future, ExecutionContext }
import akka.util.Timeout
import play.api.libs.iteratee.{ Enumerator, Iteratee }
import play.api.libs.json.JsValue
import akka.pattern._
import scala.concurrent.duration._

class ClientActorController extends Actor with ActorLogging {
  import ExecutionContext.Implicits.global
  context.system.scheduler.schedule(ConsoleActorSystemGlobal.scheduleFrequency, ConsoleActorSystemGlobal.scheduleFrequency, self, Tick)

  def receive = {
    case CreateClient(id) ⇒
      if (context.child(id).isEmpty) context.actorOf(Props[ClientHandlerActor], id) forward InitializeCommunication
    case Tick ⇒ context.children foreach { _ ! Tick }
  }
}

object ClientController {
  import play.api.libs.concurrent.Execution.Implicits._
  implicit val timeout = Timeout(1.second)
  def join(id: String): Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {
    (ConsoleActorSystemGlobal.clientHandler ? CreateClient(id)).map {
      case Connection(ref, enumerator) ⇒ (Iteratee.foreach[JsValue] { ref ! HandleRequest(_) }.map(_ ⇒ ref ! PoisonPill), enumerator)
    }
  }
}

case class CreateClient(id: String)
case class Connection(ref: ActorRef, enum: Enumerator[JsValue])
case class HandleRequest(payload: JsValue)
case class Update(js: JsValue)
case object InitializeCommunication
case object Tick
