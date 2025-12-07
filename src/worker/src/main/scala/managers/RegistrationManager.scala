package managers

import io.grpc.ManagedChannel
import services.WorkerRegistrationService
import repositories.GrpcRegisterRepository

import java.net.{InetAddress, NetworkInterface}
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter

class RegistrationManager(
                           channel: ManagedChannel
                         ) {

  private val repo = new GrpcRegisterRepository(channel)
  private val service = new WorkerRegistrationService(repo)

  def start(): (Int, Int) = {
    // 1. 자신의 실제 IP 찾기 (127.0.0.1 제외)
    val myIp = getPrivateIp

    val myPort = 5002

    //println(s"[Init] Detected Address: $myIp:$myPort")

    service.register(myIp, myPort)
  }

  private def getPrivateIp: String = {
    NetworkInterface.getNetworkInterfaces.asScala
      .flatMap(_.getInetAddresses.asScala)
      .find(addr => !addr.isLoopbackAddress && addr.isSiteLocalAddress && !addr.getHostAddress.contains(":"))
      .map(_.getHostAddress)
      .getOrElse(InetAddress.getLocalHost.getHostAddress)
  }
}