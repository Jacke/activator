/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package actors

import play.api.Play

object ConsoleConfig {
  lazy val consoleHost = Play.current.configuration.getString("console.host").getOrElse("localhost")
  lazy val consolePort = Play.current.configuration.getInt("console.port").getOrElse(9898)
  lazy val consoleStartURL = Play.current.configuration.getString("console.start-url").getOrElse("/monitoring/")
  lazy val timeSeriesMaxPoints = Play.current.configuration.getInt("timeseries.max-points").getOrElse(300)
  lazy val scatterMaxPoints = Play.current.configuration.getInt("scatter.max-points").getOrElse(5000)
  lazy val metadataExpiration = (Play.current.configuration.getMilliseconds("query.cache-metadata-expiration").getOrElse(30000L) / 1000).toInt
  lazy val historicalExpiration = (Play.current.configuration.getMilliseconds("query.cache-historical-expiration").getOrElse(60000L) / 1000).toInt
  lazy val maxPointsFilter = "&maxPoints=" + timeSeriesMaxPoints
  lazy val scatterMaxPointsFilter = "&maxPoints=" + scatterMaxPoints
}
