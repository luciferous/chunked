package chunked.finagle

import com.twitter.concurrent.Broker
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.stream.{Stream, StreamResponse}
import com.twitter.finagle.stream.EOF
import com.twitter.util.Future
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponseStatus.OK
import org.jboss.netty.util.HashedWheelTimer
import org.jboss.netty.util.TimerTask
import org.jboss.netty.util.Timeout

class RepeatChunk(
  messages: Broker[ChannelBuffer],
  errors: Broker[Throwable],
  bytes: Array[Byte]) extends TimerTask {
  def run(to: Timeout) {
    val buf = ChannelBuffers.wrappedBuffer(bytes)
    messages.send(buf) andThen errors.send(EOF)
    Main.timer.newTimeout(to.getTask(), 1, TimeUnit.SECONDS)
  }
}

class SimpleStream extends Service[HttpRequest, StreamResponse] {
  def apply(request: HttpRequest) = Future {
    val messageBroker = new Broker[ChannelBuffer]
    val errors = new Broker[Throwable]
    Main.timer.newTimeout(
      new RepeatChunk(messageBroker, errors, Array(65.toByte)),
      1, TimeUnit.SECONDS)
    new StreamResponse {
      val httpResponse =
        new DefaultHttpResponse(request.getProtocolVersion, OK)
      def messages = messageBroker.recv
      def error = errors.recv
      def release() = {
        messageBroker.recv foreach { _ => () }
      }
    }
  }
}

object Main {
  val timer: HashedWheelTimer = new HashedWheelTimer()
  def main(args: Array[String]) {
    val port = Integer.parseInt(args(0))
    val server = ServerBuilder()
      .codec(Stream())
      .bindTo(new InetSocketAddress(port))
      .name("httpserver")
      .build(new SimpleStream)
  }
}
