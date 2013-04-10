name := "chunked"

version := "1.0"

scalaVersion := "2.10.0"

resolvers += "twitter-repo" at "http://maven.twttr.com"

libraryDependencies += "io.netty" % "netty" % "3.6.3.Final"

libraryDependencies += "com.twitter" %% "finagle-stream" % "6.2.1"

libraryDependencies += "com.twitter" %% "finagle-http" % "6.2.1"

libraryDependencies += "com.twitter" %% "util-core" % "6.2.4"
