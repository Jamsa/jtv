package com.github.jamsa.jtv.server

import com.github.jamsa.jtv.server.network.Server

object JtvMain{
  def main(args: Array[String]): Unit = {
    Server.startup()
  }
}


