package com.github.jamsa.jtv.client.manager

import java.awt.{Robot, Toolkit}
import java.awt.event.{InputEvent, KeyEvent, MouseEvent}
import java.awt.image.BufferedImage
import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Observable
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executors

import com.github.jamsa.jtv.client.capture.ScreenCapture
import com.github.jamsa.jtv.client.network.ConnectionFactory
import com.github.jamsa.jtv.common.model.{FileTransferStart, _}
import com.github.jamsa.jtv.common.network.{Connection, ConnectionCallback}
import com.typesafe.scalalogging.Logger
import io.netty.channel.{ChannelFuture, ChannelHandlerContext}
import javax.swing.filechooser.FileSystemView


object MainFrameManager extends Observable{
  val logger  = Logger(MainFrameManager.getClass)

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
          case m:FileTransferRequest =>receiveFileTransferReq(m)
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

  /*=============被控服务端=============*/
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
    //logger.info(s"发送屏幕消息${msg}")
    beControlConnection.foreach(_.sendMessage(msg))
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
    beControlConnection = beControlConnection match {
      case None => {
        ConnectionFactory.createConnection(new ConnectionCallback {
          override def onClose(future: ChannelFuture): Unit = {
            stopCapture()
            beControlConnection = None
          }

          override def onMessage(ctx: ChannelHandlerContext, message: JtvMessage): Unit = {
            message match {
              case m:MouseEventMessage => receiveMouseEvent(m)
              case m:KeyEventMessage => receiveKeyEvent(m)
              case m:FileListRequest => receiveFileListRequest(m)
              case _ => logger.warn(s"接收到无法处理的控制消息${message}")
            }
          }
        })
      }
      case Some(_) => beControlConnection
    }

    beControlConnection.foreach(conn => {
      logger.info(s"接受控制请求${controlRequest}")
      conn.sendMessage(ControlResponse(true,"接受控制",controlRequest.sourceSessionId,controlRequest.sourceChannelId.getOrElse(""),ConnectionFactory.getSessionId))
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

  def receiveFileListRequest(fileListRequest: FileListRequest)={
    logger.info(s"接收文件列表请求${fileListRequest}")
    val directory = fileListRequest.directory
    if(directory.exists()){
      val listDirectory = if(directory.isDirectory) directory else directory.getParentFile
      val files = listDirectory.listFiles().sortBy(_.getName).map(f => {
        new FileInfo(f)
      })
      logger.info(s"发送文件列表，文件数量为${files.length}")
      beControlConnection.foreach(_.sendMessage(FileListResponse(listDirectory,files)))
    }
  }


  def receiveFileTransferReq(fileTransferRequest: FileTransferRequest)={
    logger.info(s"接收到文件传输请求${fileTransferRequest}")
    new FileTransferReceiveThread(fileTransferRequest).start()
  }
}

class RemoteFrameManager(val targetSessionId:Int,val targetSessionPassword:String) extends Observable{
  val logger  = Logger(classOf[RemoteFrameManager])

  var connection:Option[Connection] = None

  var control = true

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
          case m:FileListResponse => receiveFileListResp(m)
          case _ => logger.warn(s"远程控制连接接收到无法处理的消息${message}")
        }
      }
    })
  }

  def toggleControl(): Unit ={
    control = !control
  }

  /**
    * 发送控制事件
    * @param event 事件
    * @param width 事件发生时屏幕宽
    * @param height 事件发生时的屏幕高
    */
  def sendEvent(event:InputEvent,width:Int,height:Int): Unit ={
    logger.info(s"发送控制事件${event}")
    if(control) connection.foreach(_.sendMessage(JtvMessage(event,width,height)))
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
    //logger.info(s"接收到图像${screenCapture}")
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

  /**
    * 收到文件列表
    * @param fileListResponse
    */
  def receiveFileListResp(fileListResponse: FileListResponse): Unit ={
    logger.info(s"收到文件列表响应，包含${fileListResponse.files.length}个文件")
    setChanged()
    this.notifyObservers(fileListResponse)
  }

  def sendFileListRequest(fileListRequest: FileListRequest)={
    logger.info(s"发送文件列表请求${fileListRequest}")
    connection.foreach(_.sendMessage(fileListRequest))
  }

  //https://stackoverflow.com/questions/585534/what-is-the-best-way-to-find-the-users-home-directory-in-java
  /**
    * 本地文件列表
    * @param directory 目录
    * @return
    */
  def listFile(directory:File): Array[FileInfo] ={
    directory.listFiles().sortBy(_.getName).map(f => {
      new FileInfo(f)
    })
  }

  /**
    * 默认的本地文件列表
    * @return
    */
  def listFile:Array[FileInfo] = {
    //listFile(new File(System.getProperty("user.home")))
    //FileSystemView.getFileSystemView.getRoots.toList.flatMap(f => listFile(f)).toList
    FileSystemView.getFileSystemView.getRoots.map(f=>{
      new FileInfo(f)
    })
  }

  //文件传输线程池
  //var fileTransferQue = new util.LinkedList[FileTransferStart]()
  val fileId = new AtomicInteger()
  val executors = Executors.newSingleThreadExecutor()

  def tranferFile(transferType: FileTransferRequestType.FileTransferRequestType,from:FileInfo,to:FileInfo): Unit ={
    val task = FileTransferStart(fileId.addAndGet(1).toString,transferType,from,to)
    /*synchronized(fileTransferQue){
      fileTransferQue.add(task)
    }*/
    var fileTransferThread = new FileTransferIssueThread(this,task)
    executors.submit(fileTransferThread)
  }

  sealed case class RefreshFileList()

  def refreshFileList()={
    setChanged()
    this.notifyObservers(RefreshFileList())
  }
}

/**
  * 文件传输发起端
  * @param remoteFrameManager
  * @param task
  */
class FileTransferIssueThread(remoteFrameManager: RemoteFrameManager,task:FileTransferStart) extends Thread{
  val logger  = Logger(classOf[FileTransferIssueThread])
  var transferFile:Option[FileOutputStream] = None

  val fileTransferConnection = ConnectionFactory.createConnection( new ConnectionCallback {
    override def onClose(future: ChannelFuture): Unit = {
      //remoteFrameManager.receiveErrorMessage(ErrorMessage("文件传输连接被关闭"))
    }

    override def onMessage(ctx: ChannelHandlerContext, message: JtvMessage): Unit = {
      message match {
        case m:FileTransferResponse => receiveFileTransferResp(m)
        case m:FileTransferData => receiveFileTransferData(m)
        case m:FileTransferEnd => receiveFileTransferEnd(m)
        case _ => logger.warn(s"文件传输接收到无法处理的消息${message}")
      }
    }
  })

  override def run(): Unit = {
    fileTransferConnection.foreach(c =>{
      val req = new FileTransferRequest(remoteFrameManager.targetSessionId,remoteFrameManager.targetSessionPassword,ConnectionFactory.getSessionId.get)
      logger.info(s"发送连接请求${req}")
      c.sendMessage(req)
    })
  }

  def receiveFileTransferResp(fileTransferResponse: FileTransferResponse): Unit ={
    logger.info(s"文件传输连接响应:${fileTransferResponse}")
    fileTransferConnection.foreach(c=>{
      //开始传输
      logger.info(s"发送开始传输消息:${task}")
      c.sendMessage(task)

      val toFile = task.to.file
      val fromFile = task.from.file

      task.fileType match {
        case FileTransferRequestType.PUT =>{
          //如果是发送，则读取文件
          var ins = None:Option[FileInputStream]
          var len = 0
          var pos = 1
          val buf = new Array[Byte](4096)
          try {
            ins = Some(new FileInputStream(fromFile))
            while({len=ins.get.read(buf);len != -1 && !Thread.interrupted()}){
              logger.info(s"发送文件数据${pos},${pos+len}")
              val fileData = FileTransferData(task.fileId,pos,pos+len,buf)
              c.sendMessage(fileData)
              pos = pos + len
            }
            //stopFileTransfer(None)
            val endMsg = FileTransferEnd(task.fileId)
            logger.info(s"发送文件结束消息:${endMsg}")
            c.sendMessage(endMsg)
            remoteFrameManager.refreshFileList()
          }catch{
            case e:Exception => {
              logger.error("文件读取出错",e)
              stopFileTransfer(Some("文件读取出错"))
            }
            case e:InterruptedException => {
              logger.error("文件传输中断",e)
              Thread.currentThread().interrupt()
              stopFileTransfer(Some("文件传输被中断"))
            }
          }finally {
            if(ins.isDefined) ins.get.close()
          }
        }
        case FileTransferRequestType.GET =>{
          //如果是读取，则建立文件
          val targetFile = new File(toFile.getAbsolutePath+"/"+fromFile.getName)

          if(!targetFile.exists()){
            transferFile = Some(new FileOutputStream(targetFile))
          }else{
            stopFileTransfer(Some("无法接收文件，文件已经存在"))
          }
        }
      }
    })
  }

  def stopFileTransfer(errmsg:Option[String]):Unit={
    fileTransferConnection.foreach(c =>{
      errmsg match {
        case Some(msg) => c.sendMessage(ErrorMessage(msg))
        case _ => None
      }
      //c.sendMessage(FileTransferEnd(task.fileId))
      c.close()
    })
    remoteFrameManager.refreshFileList()
  }

  def receiveFileTransferData(fileTransferData: FileTransferData):Unit ={
    logger.info(s"接收文件数据${fileTransferData.fromOffset},${fileTransferData.toOffset}")
    if(task.fileType==FileTransferRequestType.GET)
      transferFile.foreach(_.write(fileTransferData.data))
    else{
      //错误的传输，为PUT时，不应试收到FileTransferData
      transferFile.foreach(_.close())
      stopFileTransfer(Some("传输错误"))
      throw new RuntimeException("传输错误")
    }
  }

  def receiveFileTransferEnd(fileTransferEnd: FileTransferEnd):Unit={
    logger.info(s"收到文件传输结束信息：${fileTransferEnd}")
    transferFile.foreach(_.close())
    fileTransferConnection.foreach(_.close())
    remoteFrameManager.refreshFileList()
  }
}



/**
  * 文件传接受端
  * @param request
  */
class FileTransferReceiveThread(request:FileTransferRequest) extends Thread{
  val logger  = Logger(classOf[FileTransferReceiveThread])
  var transferFile:Option[FileOutputStream] = None
  var task:Option[FileTransferStart] = None

  val fileTransferConnection = ConnectionFactory.createConnection( new ConnectionCallback {
    override def onClose(future: ChannelFuture): Unit = {
      //MainFrameManager.receiveErrorMessage(ErrorMessage("文件传输连接被关闭"))
    }

    override def onMessage(ctx: ChannelHandlerContext, message: JtvMessage): Unit = {
      message match {
        case m:FileTransferStart => receiveFileTransferStart(m)
        case m:FileTransferData => receiveFileTransferData(m)
        case m:FileTransferEnd => receiveFileTransferEnd(m)
        case _ => logger.warn(s"文件传输接收到无法处理的消息${message}")
      }
    }
  })

  override def run(): Unit = {
    fileTransferConnection.foreach(c=>{
      val resp = FileTransferResponse(true,"接受传输",request.sourceSessionId,request.sourceChannelId.get,ConnectionFactory.getSessionId)
      logger.info(s"发送接受传输响应:${resp}")
      c.sendMessage(resp)
    })
  }

  def receiveFileTransferStart(fileTransferStart: FileTransferStart): Unit ={
    logger.info(s"收到文件传输启动消息:${fileTransferStart}")

    fileTransferConnection.foreach(c=>{
      task = Some(fileTransferStart)
      val toFile = fileTransferStart.to.file
      val fromFile = fileTransferStart.from.file

      fileTransferStart.fileType match {
        case FileTransferRequestType.GET =>{
          //如果是读取，则读取文件
          var ins = None:Option[FileInputStream]
          var len = 0
          var pos = 1
          val buf = new Array[Byte](4096)
          try {
            ins = Some(new FileInputStream(fromFile))
            while({len=ins.get.read(buf);len != -1 && !Thread.interrupted()}){
              logger.info(s"发送文件数据${pos},${pos+len}")
              val fileData = FileTransferData(fileTransferStart.fileId,pos,pos+len,buf)
              c.sendMessage(fileData)
              pos = pos + len
            }
            //stopFileTransfer(None)
            val endMsg = FileTransferEnd(fileTransferStart.fileId)
            logger.info(s"发送文件结束消息:${endMsg}")
            c.sendMessage(endMsg)
          }catch{
            case e:Exception => {
              logger.error("文件读取出错",e)
              stopFileTransfer(Some("文件读取出错"))
            }
            case e:InterruptedException => {
              logger.error("文件传输中断",e)
              Thread.currentThread().interrupt()
              stopFileTransfer(Some("文件传输被中断"))
            }
          }finally {
            if(ins.isDefined) ins.get.close()
          }
        }
        case FileTransferRequestType.PUT =>{
          //如果是写入，则建立文件
          val targetFile = new File(toFile.getAbsolutePath+"/"+fromFile.getName)
          if(!targetFile.exists()){
            transferFile = Some(new FileOutputStream(targetFile))
          }else{
            stopFileTransfer(Some("无法接收文件，文件已经存在"))
          }
        }
      }
    })
  }

  def stopFileTransfer(errmsg:Option[String]):Unit={
    fileTransferConnection.foreach(c =>{
      errmsg match {
        case Some(msg) => c.sendMessage(ErrorMessage(msg))
        case _ => None
      }
      //c.sendMessage(FileTransferEnd(task.get.fileId))
      c.close()
    })
  }

  def receiveFileTransferData(fileTransferData: FileTransferData):Unit ={
    logger.info(s"接收文件数据${fileTransferData.fromOffset},${fileTransferData.toOffset}")
    if(task.get.fileType==FileTransferRequestType.PUT)
      transferFile.foreach(_.write(fileTransferData.data))
    else{
      //错误的传输，为GET时，不应试收到FileTransferData
      transferFile.foreach(_.close())
      stopFileTransfer(Some("传输错误"))
      throw new RuntimeException("传输错误")
    }
  }

  def receiveFileTransferEnd(fileTransferEnd: FileTransferEnd):Unit={
    logger.info(s"收到文件传输结束信息：${fileTransferEnd}")
    transferFile.foreach(_.close())
    fileTransferConnection.foreach(_.close())
  }
}
