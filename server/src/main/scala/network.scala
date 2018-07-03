package com.github.jamsa.jtv.server.network

import com.github.jamsa.jtv.common.codec.{JtvFrameEncoder, JtvMessageDecode, JtvMessageEncode, JtvFrameDecoder}
import com.github.jamsa.jtv.common.model.JtvMessage
import com.typesafe.scalalogging.Logger
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.{Channel, ChannelHandlerContext, ChannelInitializer, SimpleChannelInboundHandler}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.util.concurrent.GlobalEventExecutor


class ServerHandler extends SimpleChannelInboundHandler[JtvMessage]{

  private val channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)


  override def channelRead0(ctx: ChannelHandlerContext, msg: JtvMessage): Unit = {
    print(s"接收消息:${msg}")
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    super.handlerAdded(ctx)
    channelGroup.add(ctx.channel())
  }

  override def handlerRemoved(ctx: ChannelHandlerContext): Unit = {
    super.handlerRemoved(ctx)
    channelGroup.remove(ctx.channel())
  }

}

object Server{
  private val logger = Logger(Server.getClass)
  private var ch:Option[Channel] = None
  private var bgr:Option[NioEventLoopGroup] = None
  private var wgr:Option[NioEventLoopGroup] = None

  def startup()={
    logger.info("启动...")
    val bossGroup = new NioEventLoopGroup()
    bgr = Some(bossGroup)
    val workerGroup = new NioEventLoopGroup()
    wgr = Some(workerGroup)

    val bootstrap = new ServerBootstrap()

    bootstrap.group(bossGroup,workerGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[NioSocketChannel] {
      override def initChannel(ch: NioSocketChannel): Unit = {
        val pipeline = ch.pipeline()
        pipeline.addLast("frameDecoder",new JtvFrameDecoder())
        pipeline.addLast("frameEncoder",new JtvFrameEncoder())
        pipeline.addLast("messageDecoder",new JtvMessageDecode())
        pipeline.addLast("messageEncoder",new JtvMessageEncode())
        pipeline.addLast(new ServerHandler())
      }
    })

    val feature = bootstrap.bind(10090).sync()
    logger.info("启动完毕")
    val channel = feature.channel()
    ch = Some(channel)
    //ch.closeFuture().sync()

    //shutdown()
    //workerGroup.shutdownGracefully()
    //bossGroup.shutdownGracefully()
  }

  def shutdown(): Unit ={
    logger.info("停止...")
    //channel.close()
    //channel.parent().close()
    ch.foreach(c => {
      c.close()
      c.closeFuture().sync()
    })
    for{w <- wgr
        b <- bgr}{
      w.shutdownGracefully()
      b.shutdownGracefully()
    }
    logger.info("停止完毕")
  }
}