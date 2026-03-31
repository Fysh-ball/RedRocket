package site.fysh.redrocket.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import site.fysh.redrocket.R

@Composable
fun RedRocketLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.red_rocket_logo),
        contentDescription = "Red Rocket logo",
        modifier = modifier.size(80.dp)
    )
}
