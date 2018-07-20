package com.github.jamsa.jtv.server.network

import com.github.jamsa.jtv.common.codec.{JtvFrameDecoder, JtvFrameEncoder, JtvMessageDecode, JtvMessageEncode}
import com.github.jamsa.jtv.common.model._
import com.typesafe.scalalogging.Logger
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import com.github.jamsa.jtv.common.utils.ChannelUtils
import com.github.jamsa.jtv.server.manager.{JtvServerManager}

class ServerHandler extends SimpleChannelInboundHandler[JtvMessage]{

  private val logger = Logger(classOf[ServerHandler])

  override def channelRead0(ctx: ChannelHandlerContext, msg: JtvMessage): Unit = {
    logger.info(s"接收消息:${msg}")

    val sid = ChannelUtils.getSessionId(ctx.channel())

    /*if(sid==None && !(msg.isInstanceOf[LoginRequest])){
      ctx.channel().writeAndFlush(ErrorMessage("未登录"))
      ctx.channel().close()
      return
    }*/

    msg match {
      case m:LoginRequest => {
        JtvServerManager.login(ctx,m)
      }
      case m:LogoutRequest =>{
        JtvServerManager.logout(ctx,m)
      }
      case m:ControlRequest => {
        JtvServerManager.controlReq(ctx,m)
      }
      case m:ControlResponse => {
        JtvServerManager.controlResp(ctx,m)
      }
      case m:ScreenCaptureMessage =>{
        JtvServerManager.routeMessage(ctx,m)
      }
      case m:MouseEventMessage =>{
        JtvServerManager.routeMessage(ctx,m)
      }
      case m:KeyEventMessage =>{
        JtvServerManager.routeMessage(ctx,m)
      }
      case m:FileListRequest =>{
        JtvServerManager.routeMessage(ctx,m)
      }
      case m:FileListResponse =>{
        JtvServerManager.routeMessage(ctx,m)
    }
      case _ => {
        logger.info(s"无法识别的消息，关闭连接${ctx.channel().id().asLongText()}")
        ctx.close()
      }
    }
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    super.channelActive(ctx)
    logger.info(s"新连接：${ctx.channel().id().asLongText()}")

  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    super.channelInactive(ctx)
    logger.info(s"连接断开:${ctx.channel().id().asLongText()}")
    ctx.channel().close()
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
