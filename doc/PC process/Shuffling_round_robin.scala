def roundRobinPairs(n: Int): List[List[(Int, Int)]] = {
  
  val initialPlayers = (1 to n).toList
  
  def rotate(players: List[Int]): List[Int] = {
    players.head :: players.last :: players.tail.init
  }
  
  val playerStates: LazyList[List[Int]] = 
    LazyList.iterate(initialPlayers)(rotate)
  
  playerStates.take(n - 1).map { players =>
    val len = players.length
    
    (0 until (len / 2)).map { i =>
      (players(i), players(len - 1 - i))
    }.toList
    
  }.toList
}

println("--- n = 20 ---")
val rrp = roundRobinPairs(20)
rrp.foreach(println) // 전체 페어
println(rrp.flatten.length) // 페어 수
println(rrp.flatten.toSet.size) // 유니크한 페어의 수