package com.github.jamsa.jtv.common.model

import java.awt.event.{InputEvent, KeyEvent, MouseEvent, MouseWheelEvent}
import java.awt.image.BufferedImage
import java.io.{File}

import com.github.jamsa.jtv.common.utils.{CodecUtils, ImageUtils}
import com.typesafe.scalalogging.Logger

import scala.util.{Failure, Success, Try}


//数据帧
object JtvFrameType extends Enumeration {
  type JtvFrameType = Value
  val KEY_EVENT ,MOUSE_EVENT,SCREEN_CAPTURE,LOGIN_REQUEST,LOGIN_RESPONSE,CONTROL_REQUEST,CONTROL_RESPONSE,ERROR_MESSAGE,LOGOUT_REQUEST,UNKNOW = Value
}
import JtvFrameType._
class JtvFrame(val version:Int, val msgType:JtvFrameType, val sessionId:Int, val contentLength:Int, val content:Array[Byte])

object JtvFrame{
  private val logger = Logger(JtvFrame.getClass)
  var sessionId = 0
  val version = 1
  def apply(jvtMessage: JtvMessage): Option[JtvFrame] = {
    val msgType = jvtMessage match {
      case _:ScreenCaptureMessage => SCREEN_CAPTURE
      case _:MouseEventMessage => MOUSE_EVENT
      case m:KeyEventMessage => KEY_EVENT
      case _:LoginRequest => LOGIN_REQUEST
      case _:LoginResponse => LOGIN_RESPONSE
      case _:ControlRequest => CONTROL_REQUEST
      case _:ControlResponse => CONTROL_RESPONSE
      case _:ErrorMessage => ERROR_MESSAGE
      case _:LogoutRequest => LOGOUT_REQUEST
      case _ => UNKNOW
    }

    Try(CodecUtils.encode(jvtMessage)) match {
      case Success(arr) =>Some(new JtvFrame(version,msgType,sessionId,arr.length,arr))
      case Failure(e) => {
        logger.error(s"消息转换失败：${jvtMessage}",e)
        None
      }
    }
  }

  /*
  def unapply(jtvFrame: JtvFrame): Option[JtvMessage] = {
    Try(CodecUtils.decode(jtvFrame.content)) match {
      case Success(msgObj) => Some(msgObj.asInstanceOf[JtvMessage])
      case _ => None
    }
  }*/
}

//消息
sealed trait JtvMessage
//服务端处理的会话消息
trait ServerSessionMessage extends JtvMessage
//客户端处理的会话消息
trait ClientSessionMessage extends JtvMessage
//服务端路由转发的消息
trait RoutableMessage extends JtvMessage

//远程控制消息
//case class ControlMessage() extends JtvMessage
case class ScreenCaptureMessage(val image:Array[Byte], val originWidth:Int, val originHeight:Int) extends RoutableMessage with ClientSessionMessage
case class MouseEventMessage(val id:Int,val x:Int,val y:Int,val button:Int,val wheelRotation:Int, val screenWidth:Int, val screenHeight:Int) extends RoutableMessage with ClientSessionMessage
case class KeyEventMessage(val id:Int,val keyCode:Int) extends RoutableMessage with ClientSessionMessage

case class ErrorMessage(val message:String) extends RoutableMessage with ClientSessionMessage

//登录
case class LoginRequest(val username:String,val password:String) extends ServerSessionMessage
case class LoginResponse(val result:Boolean,val message:String,val sessionId:Int,val sessionPassword:String) extends ClientSessionMessage
case class LogoutRequest(val sessionId:String) extends ServerSessionMessage

//请求控制
case class ControlRequest(val targetSessionId:Int,val targetSessionPassword:String,val sourceSessionId:Int,val sourceChannelId:Option[String]) extends ServerSessionMessage with ClientSessionMessage {
  def this(targetSessionId:Int,targetSessionPassword:String,sourceSessionId:Int)=this(targetSessionId,targetSessionPassword,sourceSessionId,None)
}
case class ControlResponse(val result:Boolean,val message:String,val sourceSessionId:Int,val sourceChannelId:String,val targetSessionId:Option[Int]) extends ServerSessionMessage with ClientSessionMessage

//文件操作
//https://www.scala-lang.org/old/node/8183.html
//https://stackoverflow.com/questions/2400794/overload-constructor-for-scalas-case-classes
case class FileInfo(val file:File,val icon:Array[Byte]){
  def this(file:File) = this(file,ImageUtils.toByteArray(ImageUtils.getFileIconImage(file)))

}
object FileTransferRequestType extends Enumeration {
  type FileTransferRequestType = Value
  val GET,PUT = Value
}
case class FileListRequest(val directory:File) extends RoutableMessage with ClientSessionMessage
case class FileListResponse(val directory:File,val files:Array[FileInfo]) extends RoutableMessage with ClientSessionMessage

case class FileTransferRequest(val targetSessionId:Int,val targetSessionPassword:String,val sourceSessionId:Int,val sourceChannelId:Option[String]) extends ServerSessionMessage with ClientSessionMessage{
  def this(targetSessionId:Int,targetSessionPassword:String,sourceSessionId:Int)=this(targetSessionId,targetSessionPassword,sourceSessionId,None)
}
case class FileTransferResponse(val result:Boolean,val message:String,val sourceSessionId:Int,val sourceChannelId:String,val targetSessionId:Option[Int]) extends ServerSessionMessage with ClientSessionMessage

case class FileTransferStart(val fileId:String,val fileType:FileTransferRequestType.FileTransferRequestType,val from:FileInfo,val to:FileInfo) extends RoutableMessage with ClientSessionMessage
case class FileTransferData(val fileId:String,val fromOffset:Long,val toOffset:Long,val data:Array[Byte]) extends RoutableMessage with ClientSessionMessage
case class FileTransferEnd(val fileId:String) extends RoutableMessage with ClientSessionMessage

/*
object RequestMessageType extends Enumeration{
  type RequestMessageType = Value
  val CONNECT_SERVER,DISCONNECT_SERVER,CONNECT_TO,WAIT_CONNECT = Value
}
//服务器交互命令
case class RequestMessage(val messageType:RequestMessageType.RequestMessageType,val sessionId:Int,val message:String,val payload:Map[String,AnyRef]) extends JtvMessage
case class ResponseMessage(val result:Boolean,val messageType:RequestMessageType.RequestMessageType,val sessionId:Int,val message:String,val payload:Map[String,AnyRef]) extends JtvMessage
*/

object JtvMessage{
  def apply(bufferedImage: BufferedImage): ScreenCaptureMessage = {
    val width = bufferedImage.getWidth
    val height = bufferedImage.getHeight

    val newWidth = 960
    val ratio = width/newWidth
    val newHeight = height*ratio.toInt

    val resizedImage = ImageUtils.resizeImage(bufferedImage,newWidth,newHeight)
    val bytes = ImageUtils.toByteArray(resizedImage)
    ScreenCaptureMessage(bytes,width,height)
  }

  def apply(inputEvent: InputEvent,screenWidth:Int,screenHeight:Int): JtvMessage = {
    inputEvent match  {
      case m:MouseWheelEvent => MouseEventMessage(m.getID,m.getX,m.getY,m.getButton,m.getWheelRotation,screenWidth,screenHeight)
      case m:MouseEvent => MouseEventMessage(m.getID,m.getX,m.getY,m.getButton,0,screenWidth,screenHeight)
      case m:KeyEvent => KeyEventMessage(m.getID,m.getKeyCode)
      case _ => throw new RuntimeException("事件类型错误")
    }
  }

  //todo:去掉各个case class上的构造器。将apply方法修改为其它名称。将所有消息的生成都集中到此处

  /*

  def unapply(message: JtvMessage): Option[AnyRef] ={
     Try {
      message match {
        case screenCapture: ScreenCaptureMessage => {
          val bis = new ByteArrayInputStream(screenCapture.image)
          Some(ImageIO.read(bis))
        }
        case mouseEvent: MouseEventMessage => Some(mouseEvent.mouseEvent)
        case keyEvent: KeyEventMessage => Some(keyEvent.keyEvent)
        case request: RequestMessage => Some(request)
        case response: ResponseMessage => Some(response)
        case _ => None
      }
    } match{
      case Success(Some(v)) => Some(v)
      case _ => None
    }
  }*/
}


