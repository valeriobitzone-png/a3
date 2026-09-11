package a3.core.t12

class T12Reject(reason: String) : IllegalArgumentException(reason)

class T12NetworkFault(reason: String) : Exception(reason)
