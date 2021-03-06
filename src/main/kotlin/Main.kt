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
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.random.Random

class ViewModel {
    val searchQuery = MutableStateFlow("")
    val urlsResult = searchQuery
//        .debounce(300)
        .mapLatest { query -> // important - cancel previous request!!
            searchImages(query)
        }
        .flatMapConcat {
            flow {
                emit(emptyList())
                delay(10) // `produceState` does not want to replace one image with another...
                emit(it)
            }
        }

    private val client = OkHttpClient()

    suspend fun loadImageBitmap(url: String): ByteArray {
        delay(Random.nextLong(500, 1000))
        val request = Request.Builder().url(url).build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().body!!.bytes()
        }
    }

    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun searchImages(query: String): List<String> {
        println("query = $query")
        val url = "https://imsea.herokuapp.com/api/1"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .build()
        val request = Request.Builder().url(url).build()
        val resultStr = ioScope.async {
            val response = client.newCall(request).execute()
//            Thread.sleep(2000)
            response.body!!.string()
        }.await() // withContext works bad! It can not cancel the coroutine without blocking.
        // THIS IS A COROUTINE BUG, SHOULD CREATE AN ISSUE ABOUT IT

        println("end query = $query")
        val imageUrls = JsonParser.parseString(resultStr).asJsonObject.get("results")
            .asJsonArray.map { it.asString }
        return imageUrls
    }
}

private val boxColors = listOf("E3F2FD", "FCE4EC", "FFFDE7", "F1F8E9").map {
    Color(
        it.substring(0..1).toInt(16),
        it.substring(2..3).toInt(16),
        it.substring(4..5).toInt(16)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun App() {
    val viewModel = ViewModel()
    DesktopMaterialTheme {
        Column(Modifier.fillMaxSize().background(Color.White).padding(10.dp)) {
            Row {
                OutlinedTextField(
                    singleLine = true,
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
                cells = GridCells.Adaptive(minSize = 220.dp)
            ) {
                items(urls) { url ->
                    val image: ByteArray? by produceState<ByteArray?>(null) {
                        value = viewModel.loadImageBitmap(url)
                    }
                    if (image != null) {
                        Image(
                            modifier = Modifier.padding(10.dp).size(200.dp),
                            bitmap = org.jetbrains.skija.Image.makeFromEncoded(image).asImageBitmap(),
                            contentDescription = null
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(10.dp).size(200.dp).clip(RectangleShape).background(boxColors.random())
                        )
                    }
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
