/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package actors.parser

import java.text.ParseException
import java.util.Date
import scala.concurrent.duration._
import akka.event.LoggingAdapter

object TimeParser {
  val MonthPattern = """([1-9][0-9]?)(month[s]?).*""".r
  val DayPattern = """([1-9][0-9]?)(d|day[s]?).*""".r
  val HourPattern = """([1-9][0-9]?)(h|hour[s]?).*""".r
  val MinutePattern = """([1-9][0-9]?)(m|min|minute[s]?).*""".r

  val DefaultTimeRange = "10minutes"
  val TimeRangePrefixes = Set("r", "range")
  val StartTimePrefixes = Set("s", "start")
  val EndTimePrefixes = Set("e", "end")
  val TimePrefixes = TimeRangePrefixes ++ StartTimePrefixes ++ EndTimePrefixes

  final val timeFormatter = ThreadUtcDateFormat("yyyy-MM-dd'T'HH:mm")
  final val timestampFormatter = ThreadUtcDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSS")

  private val OutputFormatPattern = "yyyy-MM-dd'T'HH:mm:ss:SSS"
  private val outputFormat = ThreadUtcDateFormat(TimeParser.OutputFormatPattern)
  private val timeFormatters = Map(
    23 -> ThreadUtcDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSS"),
    19 -> ThreadUtcDateFormat("yyyy-MM-dd'T'HH:mm:ss"),
    16 -> ThreadUtcDateFormat("yyyy-MM-dd'T'HH:mm"),
    13 -> ThreadUtcDateFormat("yyyy-MM-dd'T'HH"),
    10 -> ThreadUtcDateFormat("yyyy-MM-dd"),
    7 -> ThreadUtcDateFormat("yyyy-MM"))

  def parse(dateString: Option[String]): Option[Long] = dateString match {
    case Some(x) ⇒ parse(x)
    case _ ⇒ None
  }

  def parse(dateString: String): Option[Long] = {

    def tryParse(formatter: UtcDateFormat): Option[Long] = {
      try {
        Some(formatter.parse(dateString).getTime)
      } catch {
        case e: ParseException ⇒ None
        case e: NumberFormatException ⇒ None
      }
    }

    timeFormatters.get(dateString.length).flatMap(x ⇒ tryParse(x.get))
  }

  def format(date: Date): String = {
    outputFormat.get.format(date)
  }

  def format(timestamp: Long): String = {
    outputFormat.get.format(new Date(timestamp))
  }

  def parseTime(startTimeOption: Option[String], endTimeOption: Option[String], rollingOption: Option[String], log: Option[LoggingAdapter] = None): TimeQuery = {
    def adjustTimeFormat(queryValue: String): String = {
      val s = queryValue.replaceAll("_", "T").replaceAll(" ", "T")
      TimeParser.parse(s) match {
        case Some(x) ⇒ TimeParser.format(x)
        case _ ⇒
          for { l ← log } l.warning("Unknown time format, expected 'yyyy-MM-dd_HH:mm', got: " + queryValue + ". Defaulting to now.")
          TimeParser.format(System.currentTimeMillis)
      }
    }

    def timeRange(queryValue: String): (Duration, String) = queryValue match {
      case MinutePattern(value, _) ⇒ ((value.toInt.minutes), "minutes")
      case HourPattern(value, _) ⇒ ((value.toInt.hours), "hours")
      case DayPattern(value, _) ⇒ ((value.toInt.days), "days")
      case MonthPattern(value, _) ⇒ (((value.toInt * 30).days), "months")
      case _ ⇒ throw new IllegalArgumentException("Unknown time range format: " + queryValue)
    }

    def diffTimeRange(startTime: Long, endTime: Long): (Duration, String) = {
      val duration = (endTime - startTime).millis
      val rangeType = {
        if (duration.toMinutes < 100) "minutes"
        else if (duration.toHours < 100) "hours"
        else if (duration.toDays < 100) "days"
        else "months"
      }
      (duration, rangeType)
    }

    def parseTime(queryValue: String): Long = {
      TimeParser.parse(queryValue).getOrElse(throw new IllegalArgumentException("Unknown time format: " + queryValue))
    }

    val adjustedStartTime = for { s ← startTimeOption } yield adjustTimeFormat(s)
    val adjustedEndTime = for { e ← endTimeOption } yield adjustTimeFormat(e)
    val adjustedRolling = rollingOption.getOrElse(DefaultTimeRange)

    (adjustedStartTime, adjustedEndTime) match {
      case (None, None) ⇒
        val (duration, rangeType) = timeRange(adjustedRolling)
        TimeQuery(duration, rangeType)
      case (Some(startTime), Some(endTime)) ⇒
        val startTimeMillis = parseTime(startTime)
        val endTimeMillis = parseTime(endTime)
        val (duration, rangeType) = diffTimeRange(startTimeMillis, endTimeMillis)
        TimeQuery(duration, rangeType, Some(startTimeMillis), Some(endTimeMillis))
      case (Some(startTime), None) ⇒
        val (duration, rangeType) = timeRange(adjustedRolling)
        val startTimeMillis = parseTime(startTime)
        val endTimeMillis = startTimeMillis + duration.toMillis - 1
        TimeQuery(duration, rangeType, Some(startTimeMillis), Some(endTimeMillis))
      case (None, Some(endTime)) ⇒
        val (duration, rangeType) = timeRange(adjustedRolling)
        val endTimeMillis = parseTime(endTime)
        val startTimeMillis = endTimeMillis - (duration - 1.minute).toMillis
        TimeQuery(duration, rangeType, Some(startTimeMillis), Some(endTimeMillis))
    }
  }
}

case class TimeQuery(duration: Duration, rangeType: String, startTime: Option[Long] = None, endTime: Option[Long] = None) {
  // precondition
  (startTime, endTime) match {
    case (None, None) ⇒ // ok
    case (Some(_), Some(_)) ⇒ // ok
    case (s, e) ⇒ throw new IllegalArgumentException("startTime and endTime must be defined: [%s,%s]".
      format(startTime, endTime))
  }

  def queryParams: String = (startTime, endTime) match {
    case (Some(start), Some(end)) ⇒
      "startTime=%s&endTime=%s&rangeType=%s".format(
        TimeParser.timeFormatter.get.format(new Date(start)),
        TimeParser.timeFormatter.get.format(new Date(end)),
        rangeType)
    case (_, _) ⇒ rangeType match {
      case unit @ "minutes" ⇒ "rolling=" + duration.toMinutes + unit
      case unit @ "hours" ⇒ "rolling=" + duration.toHours + unit
      case unit @ "days" ⇒ "rolling=" + duration.toDays + unit
      case unit @ "months" ⇒ "rolling=" + (duration.toDays / 30) + unit
    }
  }

  def isRolling: Boolean = startTime.isEmpty && endTime.isEmpty

  def percentilesQueryParams: String = endTime match {
    case None ⇒ "rolling=1hour"
    case Some(end) ⇒ "time=" + TimeParser.timeFormatter.get.format(new Date(end)) + "&rangeType=hours"
  }
}
