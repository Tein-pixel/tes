# JavaMail discovers provider classes using resource metadata and reflection.
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.activation.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
