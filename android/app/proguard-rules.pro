# kotlinx.serialization keeps its generated serializers via companion objects.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.wavelink.app.core.net.**$$serializer { *; }
-keepclassmembers class ru.wavelink.app.core.net.** {
    *** Companion;
}

# Retrofit interfaces are only referenced reflectively.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
