package com.kimrosil.diplink

enum class TankType(
    val displayName: String,
    val tankModel: String,
    val maxDepthCm: Float,
    val capacityLiters: Float,
    val diameterMeters: Float,
    val lengthMeters: Float,
    val fuelColorHex: String
) {
    DIESEL(
        displayName = "Diesel",
        tankModel = "Phoenix Tank",
        maxDepthCm = 242.0f,
        capacityLiters = 22354.0f,
        diameterMeters = 2.42f,
        lengthMeters = 4.86f,
        fuelColorHex = "#FFB300"
    ),
    PREMIUM91(
        displayName = "Prem 91",
        tankModel = "Premium Tank (91)",
        maxDepthCm = 203.0f,
        capacityLiters = 16000.0f,
        diameterMeters = 2.03f,
        lengthMeters = 4.88f,
        fuelColorHex = "#00C853"
    ),
    PREMIUM95(
        displayName = "Prem 95",
        tankModel = "Premium Tank (95)",
        maxDepthCm = 203.0f,
        capacityLiters = 16000.0f,
        diameterMeters = 2.03f,
        lengthMeters = 4.88f,
        fuelColorHex = "#E53935"
    )
}

data class CalibrationResult(
    val depthCm: Float,
    val volumeLiters: Float,
    val fillPercent: Float,
    val ullageLiters: Float,
    val tank: TankType
)

object CalibrationEngine {

    val dieselData = mapOf(
        1f to 10f, 2f to 28f, 3f to 52f, 4f to 80f, 5f to 112f, 6f to 147f, 7f to 185f, 8f to 226f, 9f to 269f, 10f to 315f,
        11f to 363f, 12f to 413f, 13f to 465f, 14f to 519f, 15f to 575f, 16f to 632f, 17f to 691f, 18f to 752f, 19f to 815f, 20f to 879f,
        21f to 944f, 22f to 1011f, 23f to 1080f, 24f to 1149f, 25f to 1220f, 26f to 1292f, 27f to 1366f, 28f to 1441f, 29f to 1516f, 30f to 1593f,
        31f to 1671f, 32f to 1751f, 33f to 1831f, 34f to 1912f, 35f to 1994f, 36f to 2077f, 37f to 2162f, 38f to 2247f, 39f to 2333f, 40f to 2420f,
        41f to 2508f, 42f to 2596f, 43f to 2686f, 44f to 2776f, 45f to 2867f, 46f to 2959f, 47f to 3052f, 48f to 3145f, 49f to 3239f, 50f to 3334f,
        51f to 3430f, 52f to 3526f, 53f to 3623f, 54f to 3721f, 55f to 3819f, 56f to 3918f, 57f to 4017f, 58f to 4117f, 59f to 4218f, 60f to 4319f,
        61f to 4421f, 62f to 4524f, 63f to 4627f, 64f to 4730f, 65f to 4834f, 66f to 4939f, 67f to 5044f, 68f to 5149f, 69f to 5255f, 70f to 5361f,
        71f to 5468f, 72f to 5576f, 73f to 5683f, 74f to 5792f, 75f to 5900f, 76f to 6009f, 77f to 6119f, 78f to 6228f, 79f to 6338f, 80f to 6449f,
        81f to 6560f, 82f to 6671f, 83f to 6782f, 84f to 6894f, 85f to 7006f, 86f to 7119f, 87f to 7231f, 88f to 7345f, 89f to 7458f, 90f to 7571f,
        91f to 7685f, 92f to 7799f, 93f to 7914f, 94f to 8028f, 95f to 8143f, 96f to 8258f, 97f to 8373f, 98f to 8488f, 99f to 8604f, 100f to 8720f,
        101f to 8836f, 102f to 8952f, 103f to 9068f, 104f to 9184f, 105f to 9301f, 106f to 9417f, 107f to 9534f, 108f to 9651f, 109f to 9768f, 110f to 9885f,
        111f to 10002f, 112f to 10120f, 113f to 10237f, 114f to 10354f, 115f to 10472f, 116f to 10589f, 117f to 10707f, 118f to 10824f, 119f to 10942f, 120f to 11059f,
        121f to 11177f, 122f to 11295f, 123f to 11412f, 124f to 11530f, 125f to 11647f, 126f to 11765f, 127f to 11882f, 128f to 12000f, 129f to 12117f, 130f to 12235f,
        131f to 12352f, 132f to 12469f, 133f to 12586f, 134f to 12703f, 135f to 12820f, 136f to 12937f, 137f to 13053f, 138f to 13170f, 139f to 13286f, 140f to 13402f,
        141f to 13519f, 142f to 13634f, 143f to 13750f, 144f to 13866f, 145f to 13981f, 146f to 14096f, 147f to 14211f, 148f to 14326f, 149f to 14441f, 150f to 14555f,
        151f to 14669f, 152f to 14783f, 153f to 14896f, 154f to 15010f, 155f to 15123f, 156f to 15235f, 157f to 15348f, 158f to 15460f, 159f to 15572f, 160f to 15683f,
        161f to 15794f, 162f to 15905f, 163f to 16016f, 164f to 16126f, 165f to 16236f, 166f to 16345f, 167f to 16454f, 168f to 16562f, 169f to 16671f, 170f to 16778f,
        171f to 16886f, 172f to 16993f, 173f to 17099f, 174f to 17205f, 175f to 17310f, 176f to 17415f, 177f to 17520f, 178f to 17624f, 179f to 17727f, 180f to 17830f,
        181f to 17933f, 182f to 18035f, 183f to 18136f, 184f to 18237f, 185f to 18337f, 186f to 18436f, 187f to 18535f, 188f to 18633f, 189f to 18731f, 190f to 18828f,
        191f to 18924f, 192f to 19020f, 193f to 19115f, 194f to 19209f, 195f to 19302f, 196f to 19395f, 197f to 19487f, 198f to 19578f, 199f to 19668f, 200f to 19758f,
        201f to 19847f, 202f to 19934f, 203f to 20021f, 204f to 20107f, 205f to 20192f, 206f to 20277f, 207f to 20360f, 208f to 20442f, 209f to 20523f, 210f to 20603f,
        211f to 20683f, 212f to 20761f, 213f to 20838f, 214f to 20913f, 215f to 20988f, 216f to 21062f, 217f to 21134f, 218f to 21205f, 219f to 21274f, 220f to 21343f,
        221f to 21410f, 222f to 21475f, 223f to 21539f, 224f to 21602f, 225f to 21663f, 226f to 21722f, 227f to 21779f, 228f to 21835f, 229f to 21889f, 230f to 21941f,
        231f to 21991f, 232f to 22039f, 233f to 22085f, 234f to 22128f, 235f to 22169f, 236f to 22207f, 237f to 22242f, 238f to 22274f, 239f to 22302f, 240f to 22326f,
        241f to 22344f, 242f to 22354f
    )

    val premiumData = mapOf(
        1f to 2f, 2f to 14f, 3f to 32f, 4f to 55f, 5f to 82f, 6f to 112f, 7f to 145f, 8f to 181f, 9f to 219f, 10f to 259f,
        11f to 301f, 12f to 346f, 13f to 392f, 14f to 440f, 15f to 490f, 16f to 541f, 17f to 594f, 18f to 649f, 19f to 705f, 20f to 762f,
        21f to 821f, 22f to 881f, 23f to 942f, 24f to 1004f, 25f to 1068f, 26f to 1133f, 27f to 1199f, 28f to 1266f, 29f to 1334f, 30f to 1403f,
        31f to 1473f, 32f to 1543f, 33f to 1615f, 34f to 1688f, 35f to 1762f, 36f to 1836f, 37f to 1912f, 38f to 1988f, 39f to 2065f, 40f to 2143f,
        41f to 2221f, 42f to 2300f, 43f to 2380f, 44f to 2461f, 45f to 2542f, 46f to 2624f, 47f to 2707f, 48f to 2790f, 49f to 2874f, 50f to 2959f,
        51f to 3044f, 52f to 3130f, 53f to 3216f, 54f to 3302f, 55f to 3390f, 56f to 3478f, 57f to 3566f, 58f to 3655f, 59f to 3744f, 60f to 3833f,
        61f to 3924f, 62f to 4014f, 63f to 4105f, 64f to 4197f, 65f to 4288f, 66f to 4381f, 67f to 4473f, 68f to 4566f, 69f to 4659f, 70f to 4753f,
        71f to 4847f, 72f to 4941f, 73f to 5035f, 74f to 5130f, 75f to 5225f, 76f to 5321f, 77f to 5416f, 78f to 5512f, 79f to 5608f, 80f to 5705f,
        81f to 5801f, 82f to 5898f, 83f to 5995f, 84f to 6092f, 85f to 6189f, 86f to 6287f, 87f to 6385f, 88f to 6482f, 89f to 6580f, 90f to 6678f,
        91f to 6776f, 92f to 6875f, 93f to 6973f, 94f to 7072f, 95f to 7170f, 96f to 7269f, 97f to 7367f, 98f to 7466f, 99f to 7565f, 100f to 7664f,
        101f to 7762f, 102f to 7861f, 103f to 7960f, 104f to 8059f, 105f to 8158f, 106f to 8256f, 107f to 8355f, 108f to 8454f, 109f to 8552f, 110f to 8651f,
        111f to 8749f, 112f to 8848f, 113f to 8946f, 114f to 9044f, 115f to 9142f, 116f to 9240f, 117f to 9338f, 118f to 9436f, 119f to 9533f, 120f to 9630f,
        121f to 9728f, 122f to 9824f, 123f to 9921f, 124f to 10018f, 125f to 10114f, 126f to 10210f, 127f to 10306f, 128f to 10402f, 129f to 10497f, 130f to 10592f,
        131f to 10687f, 132f to 10782f, 133f to 10876f, 134f to 10970f, 135f to 11063f, 136f to 11157f, 137f to 11249f, 138f to 11342f, 139f to 11434f, 140f to 11526f,
        141f to 11617f, 142f to 11708f, 143f to 11799f, 144f to 11889f, 145f to 11979f, 146f to 12068f, 147f to 12157f, 148f to 12245f, 149f to 12333f, 150f to 12420f,
        151f to 12507f, 152f to 12593f, 153f to 12679f, 154f to 12764f, 155f to 12848f, 156f to 12932f, 157f to 13015f, 158f to 13098f, 159f to 13180f, 160f to 13261f,
        161f to 13342f, 162f to 13422f, 163f to 13501f, 164f to 13580f, 165f to 13658f, 166f to 13735f, 167f to 13811f, 168f to 13886f, 169f to 13961f, 170f to 14034f,
        171f to 14107f, 172f to 14179f, 173f to 14250f, 174f to 14320f, 175f to 14389f, 176f to 14457f, 177f to 14524f, 178f to 14590f, 179f to 14655f, 180f to 14718f,
        181f to 14781f, 182f to 14842f, 183f to 14902f, 184f to 14961f, 185f to 15018f, 186f to 15074f, 187f to 15128f, 188f to 15181f, 189f to 15233f, 190f to 15282f,
        191f to 15331f, 192f to 15377f, 193f to 15421f, 194f to 15464f, 195f to 15504f, 196f to 15542f, 197f to 15577f, 198f to 15610f, 199f to 15640f, 200f to 15667f,
        201f to 15690f, 202f to 15709f, 203f to 15721f
    )

    private val dieselKeys = dieselData.keys.sorted()
    private val premiumKeys = premiumData.keys.sorted()

    fun calculate(tank: TankType, depthCm: Float): CalibrationResult {
        val clampedDepth = depthCm.coerceAtLeast(0f)
        val maxDepth = tank.maxDepthCm
        val effectiveDepth = clampedDepth.coerceAtMost(maxDepth)

        val volumeLiters = interpolateVolume(tank, effectiveDepth)
        val fillPercent = ((volumeLiters / tank.capacityLiters) * 100f).coerceIn(0f, 100f)
        val ullageLiters = (tank.capacityLiters - volumeLiters).coerceAtLeast(0f)

        return CalibrationResult(
            depthCm = clampedDepth,
            volumeLiters = volumeLiters,
            fillPercent = fillPercent,
            ullageLiters = ullageLiters,
            tank = tank
        )
    }

    private fun interpolateVolume(tank: TankType, depth: Float): Float {
        if (depth <= 0f) return 0f

        val (data, keys) = if (tank == TankType.DIESEL) {
            dieselData to dieselKeys
        } else {
            premiumData to premiumKeys
        }

        if (data.containsKey(depth)) {
            return data[depth] ?: 0f
        }

        val firstKey = keys.first()
        if (depth < firstKey) {
            return (depth / firstKey) * (data[firstKey] ?: 0f)
        }

        val lastKey = keys.last()
        if (depth >= lastKey) {
            return data[lastKey] ?: 0f
        }

        val lower = keys.last { it < depth }
        val upper = keys.first { it > depth }

        val lowerVal = data[lower] ?: 0f
        val upperVal = data[upper] ?: 0f

        return lowerVal + (upperVal - lowerVal) * (depth - lower) / (upper - lower)
    }
}
