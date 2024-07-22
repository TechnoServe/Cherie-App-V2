package org.technoserve.cherieapp.database

import androidx.lifecycle.LiveData

class PredictionRepository(private val predictionDAO: PredictionDAO) {

    val readAllPredictions: LiveData<List<Prediction>> = predictionDAO.getAll()

    fun readPrediction(predictionId: Long): LiveData<List<Prediction>> {
        return predictionDAO.getPredictionById(predictionId)
    }

    suspend fun addPrediction(prediction: Prediction) {
        predictionDAO.insert(prediction)
    }

    fun getLastPrediction(): LiveData<List<Prediction>> {
       return predictionDAO.getLastPrediction()
    }

    suspend fun updatePrediction(prediction: Prediction) {
        predictionDAO.update(prediction)
    }

    suspend fun deletePrediction(prediction: Prediction) {
        predictionDAO.delete(prediction)
    }

    suspend fun deleteAllPredictions() {
        predictionDAO.deleteAll()
    }

    suspend fun updateSyncStatus(id: Long){
        predictionDAO.updateSyncStatus(id)
    }

    suspend fun updateSyncListStatus(ids: List<Long>){
        predictionDAO.updateSyncListStatus(ids)
    }

    suspend fun deleteList(ids: List<Long>){
        predictionDAO.deleteList(ids)
    }


}