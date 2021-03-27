import org.jetbrains.exposed.sql.Table

object Events : Table() {
    val runID = integer("runid")
    val eventID = integer("eventid")
    val trackID = integer("trackid")
    val stepID = integer("stepid")

    //
    val trackParentID = integer("trackparentid")
    val volumeID = integer("volumeid")
    val particleID = integer("particleid")
    val processID = integer("processid")

    // data
    val x = double("x")
    val y = double("y")
    val z = double("z")
    val eDep = double("edep")
    val kineticE = double("kinetice")
    val timeGlobal = double("timeglobal")

    // these can be null!
    val px = double("px")
    val py = double("py")
    val pz = double("pz")
    val charge = double("charge")
    val steplength = double("steplength")
    val tracklength = double("tracklength")

    // strings
    val volumeName = text("volumename")
    val processName = text("processname")
    val particleName = text("particlename")
    val materialName = text("materialname")

}