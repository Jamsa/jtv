package com.github.jamsa.jtv.client.gui

import java.awt.FlowLayout

import javax.swing.{JButton, JFrame, JLabel, JTextField}

object MainFrame extends JFrame{

  val remoteIp = new JTextField(10)
  val remotePort = new JTextField(10)

  val connectBtn = new JButton("连接")
  var connectStatus = 0
  val listenBtn = new JButton("启动监听")
  val listenStatus = 0

  def initFrame(): Unit ={
    setTitle("JTV")
    setSize(300,100)
    setAlwaysOnTop(true)
    val contentPanel = getContentPane
    contentPanel.setLayout(new FlowLayout())
    contentPanel.add(new JLabel("远程主机："))
    contentPanel.add(remoteIp)
    contentPanel.add(new JLabel("端口："))
    contentPanel.add(remotePort)
    contentPanel.add(connectBtn)
    contentPanel.add(listenBtn)

  }

  initFrame()

}