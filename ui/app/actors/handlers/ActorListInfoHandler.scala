/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package actors.handlers

import actors._
import actors.ModuleInformation
import akka.actor.ActorRef
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

class ActorListInfoHandler extends ConsoleHandlerActor {
  import ExecutionContext.Implicits.global

  def call(receiver: ActorRef, mi: ModuleInformation): Future[(ActorRef, JsValue)] = {
    val timeFilter = mi.time.queryParams
    val scopeFilter = "&" + mi.scope.queryParams
    val offset = for { pi ← mi.pagingInformation } yield pi.offset
    val offsetFilter = "&offset=" + offset.getOrElse("")
    val limit = for { pi ← mi.pagingInformation } yield pi.limit
    val limitFilter = "&limit=" + limit.getOrElse("")
    val sortCommand = for { sc ← mi.sortCommand } yield sc
    val sortCommandFilter = "&sortOn=" + sortCommand.getOrElse("")
    val actorsStatsPromise = call(ConsoleHandlerActor.actorsURL, timeFilter + scopeFilter + offsetFilter + limitFilter + sortCommandFilter)

    for {
      actorsStats ← actorsStatsPromise
    } yield {
      val result = validateResponse(actorsStats) match {
        case ValidResponse ⇒
          val data = JsObject(Seq("actors" -> actorsStats.json))
          JsObject(Seq(
            "type" -> JsString("actors"),
            "data" -> data))
        case InvalidLicense(jsonLicense) ⇒ jsonLicense
        case ErrorResponse(jsonErrorCodes) ⇒ jsonErrorCodes
      }

      (receiver, result)
    }
  }
}
