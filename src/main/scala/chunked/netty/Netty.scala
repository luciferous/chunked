package chunked.netty

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelFuture
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.DefaultHttpChunk
import org.jboss.netty.handler.codec.http.HttpChunk
import org.jboss.netty.handler.codec.http.HttpResponseEncoder
import org.jboss.netty.handler.codec.http.HttpResponseStatus.OK
import org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
import org.jboss.netty.util.HashedWheelTimer
import org.jboss.netty.util.Timeout
import org.jboss.netty.util.TimerTask

object ServerHandler {
  val timer: HashedWheelTimer = new HashedWheelTimer()
  def chunk(bytes: Array[Byte]): HttpChunk = {
    new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(bytes))
  }
}

class RepeatChunk(
  val chan: Channel,
  val chunk: HttpChunk,
  val delay: Int) extends TimerTask {
  def run(to: Timeout) {
    val future = chan.write(chunk)
    future.addListener(new ChannelFutureListener() {
      def operationComplete(f: ChannelFuture) {
        ServerHandler.timer.newTimeout(to.getTask(), 1, TimeUnit.SECONDS)
      }
    })
  }
}

class ServerHandler extends SimpleChannelUpstreamHandler {
  private[this] var sentHeaders: Boolean = false
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    if (sentHeaders) return
    sentHeaders = true
    val res = new DefaultHttpResponse(HTTP_1_1, OK)
    res.setChunked(true)
    val chan = ctx.getChannel()
    chan.write(res)
    val task = new RepeatChunk(chan, ServerHandler.chunk(Array(65.toByte)), 1)
    ServerHandler.timer.newTimeout(task, 1, TimeUnit.SECONDS)
  }
}

class ServerPipelineFactory extends ChannelPipelineFactory {
  def getPipeline():ChannelPipeline = {
    val pipeline = Channels.pipeline()
    pipeline.addLast("encoder", new HttpResponseEncoder)
    pipeline.addLast("handler", new ServerHandler)
    pipeline
  }
}

object Main {
  def main(args: Array[String]) {
    val port = Integer.parseInt(args(0))
    val bootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(
        Executors.newCachedThreadPool(),
        Executors.newCachedThreadPool()))
    bootstrap.setPipelineFactory(new ServerPipelineFactory)
    bootstrap.bind(new InetSocketAddress(port))
    ServerHandler.timer.start()
  }
}
