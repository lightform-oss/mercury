package com.lightform.mercury

import java.security.SecureRandom
import java.util.Base64

package object util {
  def generateId = Left {
    val id = new Array[Byte](16)
    random.get.nextBytes(id)

    Base64.getUrlEncoder.withoutPadding.encodeToString(id)
  }

  private val random = ThreadLocal.withInitial[SecureRandom](
    () => new SecureRandom()
  )
}
