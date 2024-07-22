package org.technoserve.cherieapp.ui.navigation

import org.technoserve.cherieapp.R

sealed class NavigationItem(var route: String, var icon: Int, var title: String) {
    object Home: NavigationItem("home", R.drawable.ic_home, "Home")
    object Inference: NavigationItem("inference", R.drawable.ic_home, "Home")
    object Logs: NavigationItem("logs", R.drawable.ic_logs, "Saved Predictions")
    object Profile: NavigationItem("profile", R.drawable.ic_profile, "Profile")
    object SelectCountry: NavigationItem("select_country",R.drawable.ic_profile,"SelectCountry")
    object Initial: NavigationItem("initial",R.drawable.ic_profile,"Initial")
    object ChangeCountry: NavigationItem("change_country",R.drawable.ic_profile,"ChangeCountry")
    object ChooseImage: NavigationItem("choose_image",R.drawable.ic_profile,"ChooseImage")
}
