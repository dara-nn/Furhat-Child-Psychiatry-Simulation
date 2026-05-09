package furhatos.app.openaichat

import furhatos.app.openaichat.flow.*
import furhatos.skills.Skill
import furhatos.flow.kotlin.*
import furhatos.nlu.LogisticMultiIntentClassifier
import org.apache.logging.log4j.LogManager
import java.io.File
import java.util.Properties

private val log = LogManager.getLogger("OpenaichatSkill")

private fun loadLocalProperties() {
    val props = Properties()
    val fromClasspath = OpenaichatSkill::class.java.classLoader.getResourceAsStream("local.properties")
    if (fromClasspath != null) {
        fromClasspath.use { props.load(it) }
        log.info("local.properties loaded from classpath ({} entries)", props.size)
    } else {
        val onDisk = File("local.properties")
        if (onDisk.exists()) {
            onDisk.inputStream().use { props.load(it) }
            log.info("local.properties loaded from disk: {} ({} entries)", onDisk.absolutePath, props.size)
        } else {
            log.warn("local.properties not found on classpath or in working dir {}", File(".").absolutePath)
            return
        }
    }
    props.forEach { (k, v) ->
        val key = k.toString()
        val value = v.toString()
        if (System.getProperty(key).isNullOrBlank() && System.getenv(key).isNullOrBlank()) {
            System.setProperty(key, value)
        }
    }
}

class OpenaichatSkill : Skill() {
    override fun start() {
        loadLocalProperties()
        Flow().run(Init)
    }
}

fun main(args: Array<String>) {
    loadLocalProperties()
    LogisticMultiIntentClassifier.setAsDefault()
    Skill.main(args)
}
