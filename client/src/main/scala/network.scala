package com.github.jamsa.jtv.client.network

import java.net.InetSocketAddress

import com.github.jamsa.jtv.common.codec.{JtvFrameDecoder, JtvFrameEncoder, JtvMessageDecode, JtvMessageEncode}
import com.github.jamsa.jtv.common.model._
import com.github.jamsa.jtv.common.network.{Connection, ConnectionCallback}
import com.github.jamsa.jtv.common.utils.ChannelUtils
import com.typesafe.scalalogging.Logger
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel

import scala.collection.mutable

class ClientHandler extends SimpleChannelInboundHandler[JtvMessage]{
  val logger = Logger[ClientHandler]

  override def channelRead0(ctx: ChannelHandlerContext, msg: JtvMessage): Unit = {
    logger.info(s"接收到消息:${msg}")
    msg match {
      /*case m:LoginResponse =>JtvClientManager.loginResp(ctx,m)
      case m:ScreenCaptureMessage => JtvClientManager.receiveScreenCapture(m)
      case m:ControlRequest =>JtvClientManager.acceptControlReq(m)
      case m:ControlResponse =>JtvClientManager.controlResp(ctx,m)
      case m:ErrorMessage => JtvClientManager.receiveErrorMessage(m)
      case m:MouseEventMessage => JtvClientManager.receiveMouseEvent(m)
      case m:KeyEventMessage => JtvClientManager.receiveKeyEvent(m)*/
      case m:ClientSessionMessage => ChannelUtils.getConnection(ctx.channel()).foreach(_.callback.onMessage(ctx,m))
      case _ => {
        logger.info(s"无法识别的消息，关闭连接${ctx.channel().id().asLongText()}")
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

object Network{
  val logger = Logger(Network.getClass)

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
    ConnectionFactory.destroySession()

    grp.map(_.shutdownGracefully())
    grp = None
    logger.info("关闭完成")
  }
}


object ConnectionFactory{
  val logger  = Logger(ConnectionFactory.getClass)

  //private var sessionChannel:Option[Channel]=None
  private val workChannels = mutable.Map[String,Channel]()
  private var sessionId:Option[Int] = None

  def getSessionId = sessionId//sessionChannel.flatMap(ChannelUtils.getSessionId(_))

  /*def createSession():Option[Channel] = {
    destroySession()
    sessionChannel = this.createConnection()
    sessionChannel.foreach(channel => {
      logger.info(s"${channel.id().asLongText()}被设置为会话连接")
    })
    sessionChannel
  }*/

  def setSessionId(sessionId:Int)={
    this.sessionId = Some(sessionId)
    /*sessionChannel.foreach(channel => {
      logger.info(s"设置会话id为：${sessionId}")
      ChannelUtils.setSessionId(channel,sessionId)
      workChannels.remove(channel.id().asLongText())
    })*/
    workChannels.values.foreach(ChannelUtils.setSessionId(_,sessionId))

  }

  private def createConnection()={
    val result = Network.createChannel

    result.foreach( ch => {
      logger.info(s"新建连接:${ch.id().asLongText()}，设置会话ID为：${sessionId}")
      sessionId.foreach(ChannelUtils.setSessionId(ch,_))
      workChannels.put(ch.id().asLongText(),ch)
      ch.closeFuture().addListener((future:ChannelFuture) =>{
        logger.info(s"工作连接${future.channel().id().asLongText()}被关闭")
        workChannels.remove(future.channel().id().asLongText())
      })
    })
    result
  }

  def createConnection(callback:ConnectionCallback):Option[Connection]={
    createConnection().map(new Connection(_,callback))
  }

  def destroySession()={
    workChannels.values.foreach(_.close().sync())
    workChannels.clear()
    //sessionChannel.foreach(_.close().sync())
    //sessionChannel = None
  }

  /*private def getSessionChannel()={
    sessionChannel
  }*/
}

