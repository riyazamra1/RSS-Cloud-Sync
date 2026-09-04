        val light = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        val surface = if (light) Color.WHITE else Color.rgb(15, 22, 36)
        val background = if (light) Color.rgb(247, 248, 252) else Color.rgb(7, 11, 20)
        val outline = if (light) Color.rgb(225, 228, 236) else Color.rgb(38, 51, 73)

        root.findViewById<ViewGroup>(id("mainScrollView"))?.setBackgroundColor(background)
        root.findViewById<NavigationView>(id("navigationView"))?.apply {
            setBackgroundColor(surface); elevation = 0f; itemIconTintList = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) menu.setGroupDividerEnabled(true)
            setItemVerticalPadding(dp(3)); setItemHorizontalPadding(dp(10)); setItemIconPadding(dp(9)); setItemIconSize(dp(22))
        }
        root.findViewById<BottomNavigationView>(id("bottomNav"))?.apply { itemIconTintList = null }
        root.findViewById<ViewGroup>(id("appearanceCard"))?.let { selector ->
            selector.background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(surface); setStroke(dp(1), outline) }
            selector.layoutParams = selector.layoutParams.apply { height = dp(48) }; selector.requestLayout()
        }
        setHeight<MaterialCardView>(root, activity, "premiumBanner", 184)