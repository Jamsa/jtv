package com.github.jamsa.jtv.client.manager

import java.awt.{Robot, Toolkit}
import java.awt.event.InputEvent
import java.awt.image.BufferedImage
import java.util.Observable

import com.github.jamsa.jtv.client.capture.ScreenCapture
import com.github.jamsa.jtv.client.gui.MainFrame
import com.github.jamsa.jtv.client.network.Client
import com.github.jamsa.jtv.common.model._
import com.github.jamsa.jtv.common.utils.ChannelUtils
import com.typesafe.scalalogging.Logger
import io.netty.channel.{Channel, ChannelFuture, ChannelHandlerContext}
import io.netty.util.concurrent.Future

import scala.collection.mutable

/*
object JtvManagerEventType extends Enumeration{
  type JtvManagerEventType = Value
  val SCREEN_CAPTURE=Value
}
case class JtvManagerEvent()
*/

object ClientSessionManager{
  val logger  = Logger(ClientSessionManager.getClass)

  private var sessionChannel:Option[Channel]=None
  private val workChannels = mutable.Map[String,Channel]()

  def getSessionId = sessionChannel.flatMap(ChannelUtils.getSessionId(_))

  def createSession():Option[Channel] = {
    destroySession()
    sessionChannel = this.createConnection()
    sessionChannel.foreach(channel => {
      logger.info(s"${channel.id().asLongText()}被设置为会话连接")
    })
    sessionChannel
  }

  def setSessionId(sessionId:Int)={

    sessionChannel.foreach(channel => {
      logger.info(s"设置会话id为：${sessionId}")
      ChannelUtils.setSessionId(channel,sessionId)
      workChannels.remove(channel.id().asLongText())
    })

  }

  def createConnection()={
    val result = Client.createChannel

    result.foreach( ch => {
      logger.info(s"新建连接:${ch.id().asLongText()}，设置会话ID为：${getSessionId}")
      getSessionId.foreach(ChannelUtils.setSessionId(ch,_))
      workChannels.put(ch.id().asLongText(),ch)
      ch.closeFuture().addListener((future:ChannelFuture) =>{
        logger.info(s"工作连接${future.channel().id().asLongText()}被关闭")
        workChannels.remove(future.channel().id().asLongText())
      })
    })
    result
  }

  def destroySession()={
    workChannels.values.foreach(_.close().sync())
    workChannels.clear()
    sessionChannel.foreach(_.close().sync())
    sessionChannel = None
  }

  def getSessionChannel()={

    sessionChannel
  }
}

object JtvClientManager extends Observable{
  val logger  = Logger(JtvClientManager.getClass)

  def connect(ip:String,port:Int): Unit ={

  }

  def listener(port:Int): Unit ={

  }

  def sendEvent(event:InputEvent,width:Int,height:Int): Unit ={
    logger.info(s"发送控制事件${event}")

    controlChannel.foreach(_.writeAndFlush(JtvMessage(event,width,height)))
  }

  var captureThread:Option[ScreenCapture] = None
  def startCapture()={
    logger.info("开始获取屏幕")
    val screenCapture = new ScreenCapture()
    captureThread=Some(screenCapture)
    screenCapture.start()
  }

  def stopCapture()={
    logger.info("停止获取屏幕")
    captureThread.foreach(_.stopCapture())
  }

  def setScreenCapture(bufferedImage: BufferedImage): Unit ={
    //this.notifyObservers(bufferedImage)
    val msg = JtvMessage(bufferedImage)
    logger.info(s"发送屏幕消息${msg}")
    beControlChannel.foreach(_.writeAndFlush(msg))
  }

  /**
    * 登录，创建会话
    * @param username
    * @param password
    */
  def loginReq(username:String,password:String): Unit ={
    ClientSessionManager.createSession().foreach(_.writeAndFlush(LoginRequest(username,password)))
  }

  /**
    * 登录响应
    * @param ctx
    * @param loginResponse 登录响应
    */
  def loginResp(ctx:ChannelHandlerContext,loginResponse: LoginResponse): Unit ={
    logger.info(s"登录响应${loginResponse}")
    ClientSessionManager.setSessionId(loginResponse.sessionId)
    this.setChanged()
    this.notifyObservers(loginResponse)
    //sessionPassword = Some(loginResponse.sessionPassword)
  }

  //控制通道
  var controlChannel:Option[Channel] = None
  /**
    * 发送连接请求
    * @param targetSessionId 目标会话
    * @param targetSessionPassword
    */
  def sendControlReq(targetSessionId:Int,targetSessionPassword:String): Unit ={
    ClientSessionManager.getSessionId.foreach(sessionId => {
      ClientSessionManager.createConnection().foreach(_.writeAndFlush(ControlRequest(targetSessionId, targetSessionPassword, sessionId, None)))
    })
  }

  /**
    * 连接控制响应
    * @param ctx
    * @param controlResponse
    */
  def controlResp(ctx:ChannelHandlerContext,controlResponse: ControlResponse): Unit ={
    controlChannel = Some(ctx.channel())
    ctx.channel().closeFuture().addListener((_:ChannelFuture) =>{
      controlChannel = None
    })
  }

  def stopControl(): Unit ={
    logger.info("停止控制")
    controlChannel.foreach(_.close())
  }



  //被控通道
  var beControlChannel:Option[Channel] = None
  /**
    * 接收连接请求
    * @param ctx 会话命令通道
    * @param controlRequest
    */
  def acceptControlReq(controlRequest: ControlRequest): Unit ={
    //从新通道返回，此通道作为被控通道
    beControlChannel = ClientSessionManager.createConnection()
    beControlChannel.foreach(channel => {
      channel.closeFuture().addListener((_:ChannelFuture) => {
        stopCapture()
        beControlChannel = None
      })
      channel.writeAndFlush(ControlResponse(true,"接受控制",controlRequest.sourceSessionId,controlRequest.sourceChannelId.getOrElse("")))
    })
    startCapture()
  }

  val tk = Toolkit.getDefaultToolkit
  val dm = tk.getScreenSize
  val robot = new Robot()

  /**
    * 接收远程桌面图像
    * @param screenCapture
    */
  def receiveScreenCapture(screenCapture: ScreenCaptureMessage): Unit ={
    logger.info(s"接收到图像${screenCapture}")
    this.setChanged()
    this.notifyObservers(screenCapture)
  }

  def receiveMouseEvent(mouseEventMessage: MouseEventMessage): Unit ={
    logger.info(s"接收鼠标事件${mouseEventMessage}")
    val mouseEvent = mouseEventMessage.mouseEvent
    val x = mouseEvent.getX/mouseEventMessage.screenWidth * dm.width
    val y = mouseEvent.getY/mouseEventMessage.screenHeight * dm.height

    //robot.mouseMove(x,y)
    //robot.mousePress(InputEvent.BUTTON2_MASK)

  }

  def receiveKeyEvent(keyEventMessage: KeyEventMessage): Unit ={
    logger.info(s"接收键盘事件${keyEventMessage}")
  }

  /**
    * 接收到错误消息
    * @param errorMessage
    */
  def receiveErrorMessage(errorMessage: ErrorMessage): Unit ={
    logger.info(s"出错:${errorMessage.message}")
    setChanged()
    this.notifyObservers(errorMessage)
  }

  def main(args: Array[String]): Unit = {
    MainFrame.setVisible(true)
    Client.startup("localhost",10090)

    JtvClientManager.loginReq("","")
    Thread.sleep(3000)
    /*ClientSessionManager.getSessionId.foreach(sessionId => {
      ClientSessionManager.createConnection().foreach(_.writeAndFlush(ControlRequest(14, "123",sessionId, None)))
    })*/
    //JtvClientManager.sendControlReq(14,"123")
    //Client.shutdown()

    //Thread.sleep(30000)
  }
}