package com.github.jamsa.jtv.client.manager

import java.awt.{Robot, Toolkit}
import java.awt.event.{InputEvent, KeyEvent, MouseEvent}
import java.awt.image.BufferedImage
import java.util.Observable

import com.github.jamsa.jtv.client.capture.ScreenCapture
import com.github.jamsa.jtv.client.network.ConnectionFactory
import com.github.jamsa.jtv.common.model._
import com.github.jamsa.jtv.common.network.{Connection, ConnectionCallback}
import com.typesafe.scalalogging.Logger
import io.netty.channel.{ChannelFuture, ChannelHandlerContext}


object MainFrameManager extends Observable{
  val logger  = Logger(MainFrameManager.getClass)

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
    beControlConnection.foreach(_.sendMessage(msg))
  }

  var sessionConnection:Option[Connection] = None
  /**
    * 登录，创建会话
    * @param username
    * @param password
    */
  def loginReq(username:String,password:String): Unit ={
    ConnectionFactory.destroySession()
    sessionConnection = ConnectionFactory.createConnection(new ConnectionCallback {
      override def onClose(future: ChannelFuture): Unit = {
        ConnectionFactory.destroySession()
      }
      override def onMessage(ctx: ChannelHandlerContext, message: JtvMessage): Unit = {
        message match {
          case m:LoginResponse =>receiveLoginResp(m)
          case m:ControlRequest =>receiveControlReq(m)
          case _ => None
        }

      }
    })
    sessionConnection.foreach(_.sendMessage(LoginRequest(username,password)))
  }

  /**
    * 登录响应
    * @param ctx
    * @param loginResponse 登录响应
    */
  private def receiveLoginResp(loginResponse: LoginResponse): Unit ={
    logger.info(s"登录响应${loginResponse}")
    ConnectionFactory.setSessionId(loginResponse.sessionId)
    this.setChanged()
    this.notifyObservers(loginResponse)
    //sessionPassword = Some(loginResponse.sessionPassword)
  }

  //被控连接
  var beControlConnection:Option[Connection] = None
  /**
    * 接收连接请求
    * @param controlRequest
    */
  def receiveControlReq(controlRequest: ControlRequest): Unit ={
    logger.info(s"接收到控制请求${controlRequest}")
    //从新通道返回，此通道作为被控通道
    beControlConnection = ConnectionFactory.createConnection(new ConnectionCallback {
      override def onClose(future: ChannelFuture): Unit = {
        stopCapture()
        beControlConnection = None
      }

      override def onMessage(ctx: ChannelHandlerContext, message: JtvMessage): Unit = {
        message match {
          case m:MouseEventMessage => receiveMouseEvent(m)
          case m:KeyEventMessage => receiveKeyEvent(m)
          case _ => logger.warn(s"接收到无法处理的控制消息${message}")
        }
      }
    })
    beControlConnection.foreach(conn => {
      logger.info(s"接受控制请求${controlRequest}")
      conn.sendMessage(ControlResponse(true,"接受控制",controlRequest.sourceSessionId,controlRequest.sourceChannelId.getOrElse("")))
      startCapture()
    })

  }

  private val tk = Toolkit.getDefaultToolkit
  private val dm = tk.getScreenSize
  private val robot = new Robot()
  def receiveMouseEvent(mouseEventMessage: MouseEventMessage): Unit ={
    logger.info(s"接收鼠标事件${mouseEventMessage}")

    mouseEventMessage.id match {
      case MouseEvent.MOUSE_PRESSED => {
        val button = mouseEventMessage.button

        button match {
          case MouseEvent.BUTTON1 => {
            robot.mousePress(InputEvent.BUTTON1_MASK)
          }
          case MouseEvent.BUTTON2 => {
            robot.mousePress(InputEvent.BUTTON2_MASK)
          }
          case MouseEvent.BUTTON3 => {
            robot.mousePress(InputEvent.BUTTON3_MASK)
          }
          case _ => None
        }
      }
      case MouseEvent.MOUSE_RELEASED | MouseEvent.MOUSE_CLICKED => {
        val button = mouseEventMessage.button

        button match {
          case MouseEvent.BUTTON1 => {
            robot.mouseRelease(InputEvent.BUTTON1_MASK)
          }
          case MouseEvent.BUTTON2 => {
            robot.mouseRelease(InputEvent.BUTTON2_MASK)
          }
          case MouseEvent.BUTTON3 => {
            robot.mouseRelease(InputEvent.BUTTON3_MASK)
          }
          case _ => None
        }
      }
      case MouseEvent.MOUSE_WHEEL =>robot.mouseWheel(mouseEventMessage.wheelRotation)
      case MouseEvent.MOUSE_MOVED | MouseEvent.MOUSE_DRAGGED => {
        val x = mouseEventMessage.x * dm.width/mouseEventMessage.screenWidth
        val y = mouseEventMessage.y * dm.height/mouseEventMessage.screenHeight
        robot.mouseMove(x, y)
        logger.info(s"(${mouseEventMessage.x},${mouseEventMessage.y})映射为(${x},${y})")
      }
      case _ => None
    }
  }

  def receiveKeyEvent(keyEventMessage: KeyEventMessage): Unit ={
    logger.info(s"接收键盘事件${keyEventMessage}")
    keyEventMessage.id match {
      case KeyEvent.KEY_PRESSED =>{
        robot.keyPress(keyEventMessage.keyCode)
      }
      case KeyEvent.KEY_RELEASED=>{
        robot.keyRelease(keyEventMessage.keyCode)
      }
    }
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

}

class RemoteFrameManager(val targetSessionId:Int,val targetSessionPassword:String) extends Observable{
  val logger  = Logger(classOf[RemoteFrameManager])

  var connection:Option[Connection] = None

  def connect(): Unit ={
    connection = ConnectionFactory.createConnection( new ConnectionCallback {
      override def onClose(future: ChannelFuture): Unit = {
        receiveErrorMessage(ErrorMessage("连接被关闭"))
      }

      override def onMessage(ctx: ChannelHandlerContext, message: JtvMessage): Unit = {
        message match {
          case m:ControlResponse => receiveControlResp(m)
          case m:ScreenCaptureMessage => receiveScreenCapture(m)
          case m:ErrorMessage => receiveErrorMessage(m)
          case _ => logger.warn(s"接收到无法处理的消息${message}")
        }
      }
    })
  }

  /**
    * 发送控制事件
    * @param event 事件
    * @param width 事件发生时屏幕宽
    * @param height 事件发生时的屏幕高
    */
  def sendEvent(event:InputEvent,width:Int,height:Int): Unit ={
    logger.info(s"发送控制事件${event}")
    connection.foreach(_.sendMessage(JtvMessage(event,width,height)))
  }

  /**
    * 发送连接请求
    */
  def sendControlReq(): Unit ={
    logger.info(s"发送控制申请至：${targetSessionId}-${targetSessionPassword}")
    for(sessionid <- ConnectionFactory.getSessionId; conn <- connection){
      conn.sendMessage(ControlRequest(targetSessionId,targetSessionPassword,sessionid,None))
    }
  }

  /**
    * 连接控制响应
    * @param controlResponse
    */
  def receiveControlResp(controlResponse: ControlResponse): Unit ={
    logger.info(s"连接控制响应:${controlResponse}")
  }

  def stopControl(): Unit ={
    logger.info("停止控制")
    connection.foreach(_.close())
  }

  /**
    * 接收远程桌面图像
    * @param screenCapture
    */
  def receiveScreenCapture(screenCapture: ScreenCaptureMessage): Unit ={
    logger.info(s"接收到图像${screenCapture}")
    this.setChanged()
    this.notifyObservers(screenCapture)
  }

  /**
    * 接收到错误事件
    * @param errorMessage
    */
  def receiveErrorMessage(errorMessage: ErrorMessage): Unit ={
    logger.info(s"出错:${errorMessage.message}")
    setChanged()
    this.notifyObservers(errorMessage)
  }

}