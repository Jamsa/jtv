package com.github.jamsa.jtv.client.network

import java.net.InetSocketAddress

import com.github.jamsa.jtv.client.manager.{ClientSessionManager, JtvClientManager}
import com.github.jamsa.jtv.common.codec.{JtvFrameDecoder, JtvFrameEncoder, JtvMessageDecode, JtvMessageEncode}
import com.github.jamsa.jtv.common.model._
import com.typesafe.scalalogging.Logger
import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel, ChannelHandlerContext, ChannelInitializer, SimpleChannelInboundHandler}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel

class ClientHandler extends SimpleChannelInboundHandler[JtvMessage]{
  val logger = Logger[ClientHandler]


  override def channelRead0(ctx: ChannelHandlerContext, msg: JtvMessage): Unit = {
    logger.info(s"接收到消息:${msg}")
    msg match {
      case loginRequest:LoginResponse =>{
        JtvClientManager.loginResp(ctx,loginRequest)
      }
    }
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    super.handlerAdded(ctx)
    //channelGroup.add(ctx.channel())
  }

  override def handlerRemoved(ctx: ChannelHandlerContext): Unit = {
    super.handlerRemoved(ctx)
    //channelGroup.remove(ctx.channel())
  }
}

object Client{
  val logger = Logger(Client.getClass)

  //private var channel:Option[Channel] = None
  private var boot:Option[Bootstrap] = None
  private var grp:Option[NioEventLoopGroup] = None

  def startup(host:String,port:Int)={
    logger.info("启动...")
    val group = new NioEventLoopGroup()
    grp = Some(group)
    val bootstrap = new Bootstrap()
    boot = Some(bootstrap)
    bootstrap.group(group)
    bootstrap.channel(classOf[NioSocketChannel]).remoteAddress(new InetSocketAddress(host,port) )
        .handler(new ChannelInitializer[NioSocketChannel] {
      override def initChannel(ch: NioSocketChannel): Unit = {
        val pipeline = ch.pipeline()
        pipeline.addLast("frameDecoder",new JtvFrameDecoder())
        pipeline.addLast("frameEncoder",new JtvFrameEncoder())
        pipeline.addLast("messageDecoder",new JtvMessageDecode())
        pipeline.addLast("messageEncoder",new JtvMessageEncode())
        pipeline.addLast(new ClientHandler())
      }
    })

    //val feature = bootstrap.connect().sync()
    //channel = Some(feature.channel())

    //channel.closeFuture().sync()

    //shutdown()
    //group.shutdownGracefully()
    logger.info("启动完毕")
  }

  def createChannel = {
    boot.map(b => b.connect().sync().channel())
  }

  def shutdown(): Unit ={
    logger.info("关闭...")
    /*channel.map(c=>{
      c.close()
      c.closeFuture().sync()
    })*/
    ClientSessionManager.destroySession()

    grp.map(_.shutdownGracefully())
    grp = None
    //channel = None
    logger.info("关闭完成")
  }

  /*def send(obj:JtvMessage)={
    channel.foreach(_.writeAndFlush(obj))
  }*/
}
