package com.github.jamsa.jtv.server

import com.github.jamsa.jtv.server.network.Server

object JtvServerMain{
  def main(args: Array[String]): Unit = {
    Server.startup()
  }
}


