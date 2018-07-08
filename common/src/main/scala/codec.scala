package com.github.jamsa.jtv.common.codec

import java.util

import com.github.jamsa.jtv.common.model.{ErrorMessage, JtvFrame, JtvFrameType, JtvMessage}
import com.github.jamsa.jtv.common.utils.{ChannelUtils, CodecUtils}
import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.{MessageToByteEncoder, MessageToMessageDecoder, MessageToMessageEncoder, ReplayingDecoder}


class JtvFrameEncoder extends MessageToByteEncoder[JtvFrame]{
  override def encode(ctx: ChannelHandlerContext, msg: JtvFrame, out: ByteBuf): Unit = {
    out.writeInt(msg.version)
    out.writeInt(msg.msgType.id)
    //out.writeInt(msg.sessionId)
    val sessionId = ChannelUtils.getSessionId(ctx.channel())
    out.writeInt(sessionId.getOrElse(0))
    out.writeInt(msg.contentLength)
    out.writeBytes(msg.content)
  }
}

object JtvFrameDecodeState extends Enumeration{
  type JtvFrameDecodeState = Value
  val VERSION,MSGTYPE,SESSIONID,CONTENTLENTH,CONTENT = Value
}
import JtvFrameDecodeState._
class JtvFrameDecoder extends ReplayingDecoder[JtvFrameDecodeState]{

  var version,contentLength:Int=_
  var sessionId:Int = _
  var msgType = JtvFrameType.LOGIN_REQUEST
  var content:Array[Byte]=_

  def reset: Unit ={
    state(VERSION)
    version = 1
    msgType = JtvFrameType.LOGIN_REQUEST
    sessionId = 0
    contentLength = 0
    content = Array[Byte](0)
  }

  reset


  override def decode(ctx: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {

    def decode: Unit = state() match {
      case VERSION => {
        version = in.readInt()
        checkpoint(MSGTYPE)
        decode
      }
      case MSGTYPE => {
        msgType = JtvFrameType(in.readInt())
        checkpoint(SESSIONID)
        decode
      }
      case SESSIONID => {
        sessionId = in.readInt()
        /*val currentSessionId = ChannelUtils.getSessionId(ctx.channel())
        currentSessionId match {
          case Some(sId)  => {
            if(sId != sessionId.toString){
              ctx.channel().writeAndFlush(new ErrorMessage("会话ID不一致!"))
              ctx.channel().close()
              return
            }
          }
          case None => ChannelUtils.setSessionId(ctx.channel(),sessionId)
        }*/

        checkpoint(CONTENTLENTH)
        decode
      }
      case CONTENTLENTH => {
        contentLength = in.readInt()
        checkpoint(CONTENT)
        decode
      }
      case CONTENT => {
        content =  ByteBufUtil.getBytes(in.readBytes(contentLength))
        out.add(new JtvFrame(version,msgType,sessionId,contentLength,content))
        reset
        return
      }
      case _ => {
        reset
        ctx.close()
        throw new Exception("Unknown decoding state: " + state())
      }
    }

    decode
  }

}

class JtvMessageDecode extends MessageToMessageDecoder[JtvFrame]{

  override def decode(ctx: ChannelHandlerContext, msg: JtvFrame, out: util.List[AnyRef]): Unit = {
    out.add(CodecUtils.decode(msg.content))
    /*msg match{
      case obj:JtvMessage => out.add(obj)
      case _ => None
    }*/
  }

}

class JtvMessageEncode extends MessageToMessageEncoder[JtvMessage]{
  override def encode(ctx: ChannelHandlerContext, msg: JtvMessage, out: util.List[AnyRef]): Unit = {
    JtvFrame(msg) match {
      case Some(m) => out.add(m)
      case _ => None
    }
    //out.add()
  }
}
