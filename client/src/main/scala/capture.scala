package com.github.jamsa.jtv.client.capture

import java.awt.{Rectangle, Robot, Toolkit}

import com.github.jamsa.jtv.client.manager.JtvClientManager

object ScreenCapture extends Thread{

  val tk = Toolkit.getDefaultToolkit
  val dm = tk.getScreenSize
  val robot = new Robot()
  val rec = new Rectangle(0,0,dm.width,dm.height)

  val interval = 500

  /*
  type CaptureCallback = BufferedImage => Unit
  var callback:Option[CaptureCallback] = None
  def startCapture(cb:Option[CaptureCallback]): Unit ={
    callback = cb
    start()
  }*/

  override def run(): Unit = {
    try {
      while (!Thread.interrupted()) {
        val startMillis = System.currentTimeMillis()
        val bufferedImage = robot.createScreenCapture(rec)
        JtvClientManager.setScreenCapture(bufferedImage);
        /*callback match {
          case Some(cb) => cb(bufferedImage)
          case _ => None
        }*/
        Thread.sleep(System.currentTimeMillis()-startMillis)
      }
    }catch {
      case _:InterruptedException => Thread.currentThread().interrupt()
    }
  }



  def startCapture(): Unit ={
    start()
  }

  def stopCapture(): Unit ={
    //callback = None
    interrupt()
  }
}
