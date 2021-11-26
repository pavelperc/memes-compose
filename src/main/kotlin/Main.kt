// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class ViewModel {
    val searchQuery = MutableStateFlow("")
    val urlsResult = searchQuery.debounce(300).map { query ->
        searchImages(query)
    }

    private val client = OkHttpClient()

    suspend fun loadImageBitmap(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().body!!.bytes()
        }
    }

    private suspend fun searchImages(query: String): List<String> {
        val url = "https://imsea.herokuapp.com/api/1"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .build()

        val request = Request.Builder().url(url).build()

        val resultStr = withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            response.body!!.string()
        }
        val imageUrls = JsonParser.parseString(resultStr).asJsonObject.get("results")
            .asJsonArray.map { it.asString }
        return imageUrls
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun App() {
    val viewModel = ViewModel()
    viewModel.searchQuery.value = "kitten"
    DesktopMaterialTheme {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row {
                TextField(
                    modifier = Modifier.weight(1f),
                    value = viewModel.searchQuery.collectAsState().value,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search") }
                )
            }
//            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
//                viewModel.itemsResult.collectAsState(emptyList()).value
//                    .forEach { message ->
//                        Text(message)
//                    }
//            }
            val urls = viewModel.urlsResult.collectAsState(emptyList()).value
            LazyVerticalGrid(
                cells = GridCells.Adaptive(minSize = 200.dp)
            ) {
                items(urls) { url ->
                    val image: ByteArray? by produceState<ByteArray?>(null) {
                        value = viewModel.loadImageBitmap(url)
                    }
                    if (image != null) {
                        Image(
                            modifier = Modifier.padding(10.dp),
                            bitmap = org.jetbrains.skija.Image.makeFromEncoded(image).asImageBitmap(),
                            contentDescription = null
                        )
                    } else Box(
                        modifier = Modifier
                            .padding(10.dp).size(200.dp).clip(RectangleShape).background(Color.White)
                    )
                }
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
