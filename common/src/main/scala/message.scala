package com.github.jamsa.jtv.common.model

import java.awt.event.{KeyEvent, MouseEvent}
import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.github.jamsa.jtv.common.utils.{CodecUtils, ImageUtils}

import scala.util.{Success, Try}

import javax.imageio.ImageIO


//数据帧
object JtvFrameType extends Enumeration {
  type JtvFrameType = Value
  val KEY_EVENT ,MOUSE_EVENT,SCREEN_CAPTURE,LOGIN_REQUEST,LOGIN_RESPONSE,CONTROL_REQUEST,CONTROL_RESPONSE = Value
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
trait JtvMessage{}
//sealed case class JvtMessage()

//远程控制消息
//case class ControlMessage() extends JtvMessage
case class ScreenCaptureMessage(val image:Array[Byte], val originWidth:Int, val originHeight:Int) extends JtvMessage
case class MouseEventMessage(val mouseEvent: MouseEvent, val screenWidth:Int, val screenHeight:Int) extends JtvMessage
case class KeyEventMessage(val keyEvent: KeyEvent) extends JtvMessage

case class ErrorMessage(val message:String) extends JtvMessage
//登录
case class LoginRequest(val username:String,val password:String) extends JtvMessage
case class LoginResponse(val result:Boolean,val message:String,val sessionId:Int,val sessionPassword:String) extends JtvMessage
case class LogoutRequest(val sessionId:String) extends JtvMessage

//请求控制
case class ControlRequest(val targetSessionId:Int,val targetSessionPassword:String,val sourceSessionId:Int,val sourceChannelId:Option[String]) extends JtvMessage{
  def this(targetSessionId:Int,targetSessionPassword:String,sourceSessionId:Int){
    this(targetSessionId,targetSessionPassword,sourceSessionId,None)
  }
}
case class ControlResponse(val result:Boolean,val message:String,val sourceSessionId:Int,val sourceChannelId:String) extends JtvMessage

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

    val newWidth = 1280
    val ratio = width/1280
    val newHeight = height*ratio.toInt

    val resizedImage = ImageUtils.resizeImage(bufferedImage,newWidth,newHeight)
    val bos = new ByteArrayOutputStream()
    ImageIO.write(resizedImage,"png",bos)
    ScreenCaptureMessage(bos.toByteArray,width,height)
  }

  /*
  def apply(mouseEvent: MouseEvent,screenWidth:Int,screenHeight:Int): MouseEventMessage = MouseEventMessage(mouseEvent,screenWidth,screenHeight)

  def apply(keyEvent: KeyEvent): KeyEventMessage = KeyEventMessage(keyEvent)

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


