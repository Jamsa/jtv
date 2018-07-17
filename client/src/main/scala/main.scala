package com.github.jamsa.jtv.client

import java.io.{File, FileInputStream}
import java.util.Properties

import com.github.jamsa.jtv.client.gui.MainFrame
import com.github.jamsa.jtv.client.manager.MainFrameManager
import com.github.jamsa.jtv.client.manager.MainFrameManager.logger
import com.github.jamsa.jtv.client.network.{ConnectionFactory, Network}

import scala.util.{Failure, Success, Try}

object JtvClientMain {
  def main(args: Array[String]): Unit = {

    val props = new Properties()
    val file = new File(ConnectionFactory.getClass.getProtectionDomain.getCodeSource.getLocation.toURI).getParentFile.getPath+"/../bin/config.properties"
    val (host,port) =Try[(String,Int)] {
      val fis = new FileInputStream(file)
      props.load(fis)
      val h = props.getProperty("host")
      val p = props.getProperty("port").toInt
      fis.close()
      (h,p)
    } match{
      case Success((host,port)) =>(host,port)
      case failure:Failure[(String,Int)] =>{
        logger.error("读取配置文件失败，使用默认值",failure.exception)
        ("localhost",10090)
      }
    }

    MainFrame.setVisible(true)
    Network.startup(host,port)

    MainFrameManager.loginReq("","")
    //Thread.sleep(3000)
    /*ClientSessionManager.getSessionId.foreach(sessionId => {
      ClientSessionManager.createConnection().foreach(_.writeAndFlush(ControlRequest(14, "123",sessionId, None)))
    })*/
    //JtvClientManager.sendControlReq(14,"123")
    //Client.shutdown()

    //Thread.sleep(30000)
  }
}