package com.legacycode.eureka.dex

@JvmInline
value class Descriptor private constructor(val name: String) {
  companion object {
    fun from(name: String): Descriptor {
      return Descriptor(name)
    }
  }

  fun matches(keyword: String): Boolean {
    return name.substring(name.lastIndexOf('/') + 1)
      .contains(keyword, true)
  }
}