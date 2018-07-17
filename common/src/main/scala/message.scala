package com.github.jamsa.jtv.common.model

import java.awt.event.{InputEvent, KeyEvent, MouseEvent}
import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream}

import com.github.jamsa.jtv.common.utils.{CodecUtils, ImageUtils}

import scala.util.{Success, Try}
import javax.imageio.ImageIO


//数据帧
object JtvFrameType extends Enumeration {
  type JtvFrameType = Value
  val KEY_EVENT ,MOUSE_EVENT,SCREEN_CAPTURE,LOGIN_REQUEST,LOGIN_RESPONSE,CONTROL_REQUEST,CONTROL_RESPONSE,ERROR_MESSAGE,LOGOUT_REQUEST,UNKNOW = Value
}
import JtvFrameType._
class JtvFrame(val version:Int, val msgType:JtvFrameType, val sessionId:Int, val contentLength:Int, val content:Array[Byte])

object JtvFrame{
  var sessionId = 0
  val version = 1
  def apply(jvtMessage: JtvMessage): Option[JtvFrame] = {
    val msgType = jvtMessage match {
      case _:ScreenCaptureMessage => SCREEN_CAPTURE
      case _:MouseEventMessage => MOUSE_EVENT
      case _:KeyEventMessage => KEY_EVENT
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
      case _ => None
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
case class MouseEventMessage(val mouseEvent: MouseEvent, val screenWidth:Int, val screenHeight:Int) extends RoutableMessage with ClientSessionMessage
case class KeyEventMessage(val keyEvent: KeyEvent) extends RoutableMessage with ClientSessionMessage

case class ErrorMessage(val message:String) extends RoutableMessage with ClientSessionMessage

//登录
case class LoginRequest(val username:String,val password:String) extends ServerSessionMessage
case class LoginResponse(val result:Boolean,val message:String,val sessionId:Int,val sessionPassword:String) extends ClientSessionMessage
case class LogoutRequest(val sessionId:String) extends ServerSessionMessage

//请求控制
case class ControlRequest(val targetSessionId:Int,val targetSessionPassword:String,val sourceSessionId:Int,val sourceChannelId:Option[String]) extends ServerSessionMessage with ClientSessionMessage {
  /*def this(targetSessionId:Int,targetSessionPassword:String,sourceSessionId:Int){
    this(targetSessionId,targetSessionPassword,sourceSessionId,None)
  }*/
}
case class ControlResponse(val result:Boolean,val message:String,val sourceSessionId:Int,val sourceChannelId:String) extends ServerSessionMessage with ClientSessionMessage

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
    val bos = new ByteArrayOutputStream()
    ImageIO.write(resizedImage,"png",bos)
    ScreenCaptureMessage(bos.toByteArray,width,height)
  }

  def apply(inputEvent: InputEvent,screenWidth:Int,screenHeight:Int): JtvMessage = {
    inputEvent match  {
      case m:MouseEvent => MouseEventMessage(m,screenWidth,screenHeight)
      case m:KeyEvent => KeyEventMessage(m)
      case _ => throw new RuntimeException("事件类型错误")
    }
  }

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


