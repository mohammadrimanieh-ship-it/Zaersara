package com.imanieh.zaersara.util

import java.time.LocalDate

data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    fun display(): String = "%04d/%02d/%02d".format(year, month, day)
}

object JalaliCalendar {
    private val breaks = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)

    private data class JalCal(val leap: Int, val gy: Int, val march: Int)

    private fun div(a: Int, b: Int) = a / b
    private fun mod(a: Int, b: Int) = a - (a / b) * b

    private fun jalCal(jy: Int): JalCal {
        val bl = breaks.size
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jm = 0
        var jump = 0
        if (jy < jp || jy >= breaks[bl - 1]) error("Invalid Jalali year $jy")
        for (i in 1 until bl) {
            jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ++
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return JalCal(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) + div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
        return d
    }

    private fun d2g(jdn: Int): LocalDate {
        var j = 4 * jdn + 139361631
        j += div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return LocalDate.of(gy, gm, gd)
    }

    private fun j2d(jy: Int, jm: Int, jd: Int): Int {
        val r = jalCal(jy)
        return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
    }

    fun toGregorian(j: JalaliDate): LocalDate = d2g(j2d(j.year, j.month, j.day))

    fun fromGregorian(g: LocalDate): JalaliDate {
        val gy = g.year
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn = g2d(gy, g.monthValue, g.dayOfMonth)
        val jdn1f = g2d(gy, 3, r.march)
        var k = jdn - jdn1f
        val jm: Int
        val jd: Int
        if (k >= 0) {
            if (k <= 185) {
                jm = 1 + div(k, 31)
                jd = mod(k, 31) + 1
                return JalaliDate(jy, jm, jd)
            }
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k++
        }
        jm = 7 + div(k, 30)
        jd = mod(k, 30) + 1
        return JalaliDate(jy, jm, jd)
    }

    fun today(): JalaliDate = fromGregorian(LocalDate.now())
    fun isLeap(jy: Int): Boolean = jalCal(jy).leap == 0
    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (isLeap(jy)) 30 else 29
    }

    fun isoToJalali(iso: String): String = runCatching { fromGregorian(LocalDate.parse(iso)).display() }.getOrDefault(iso)
}
