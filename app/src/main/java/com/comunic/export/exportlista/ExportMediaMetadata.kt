package com.comunic.export.exportlista

data class ExportMediaItem(
    val mediaId: String,
    val displayName: String,
    val mediaType: String
)

data class ExportMediaMetadata(
    val version: Int = 2,
    val items: List<ExportMediaItem>
)

/*
* ¿Para qué sirve este archivo?

Solamente define la información que vamos a guardar en el metadata.json de un ZIP de elementos:

{
  "version": 2,
  "items": [
    {
      "mediaId": "UUID",
      "displayName": "FLOR VIOLETA",
      "mediaType": "image"
    }
  ]
}

Después ExportManager va a usar estas clases para generar ese JSON.
*
*
*
* */