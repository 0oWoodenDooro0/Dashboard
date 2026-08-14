package website.woodendoor.dashboard

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform