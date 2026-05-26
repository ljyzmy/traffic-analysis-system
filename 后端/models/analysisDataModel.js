const { getAnalysisDataCollection } = require('../db');
const { ObjectId } = require('mongodb');

/**
 * 交通分析数据模型类
 */
class AnalysisDataModel {
  /**
   * 保存分析数据
   * @param {Object} analysisData 分析数据对象
   * @returns {Promise<Object>} 保存后的数据对象
   */
  static async saveAnalysisData(analysisData) {
    const analysisCollection = await getAnalysisDataCollection();
    
    // 确保分析时间为日期对象
    if (typeof analysisData.analysisTime === 'string') {
      analysisData.analysisTime = new Date(analysisData.analysisTime);
    } else if (!analysisData.analysisTime) {
      analysisData.analysisTime = new Date();
    }
    
    // 添加创建时间
    const dataToInsert = {
      ...analysisData,
      createdAt: new Date()
    };
    
    const result = await analysisCollection.insertOne(dataToInsert);
    return { ...dataToInsert, _id: result.insertedId };
  }
  
  /**
   * 获取特定用户的所有分析数据
   * @param {string} userId 用户ID
   * @param {Object} options 查询选项
   * @returns {Promise<Array>} 分析数据数组
   */
  static async getAnalysisByUserId(userId, options = {}) {
    const { limit = 10, skip = 0, sortBy = 'analysisTime', sortOrder = -1 } = options;
    
    const analysisCollection = await getAnalysisDataCollection();
    
    const result = await analysisCollection
      .find({ userId })
      .sort({ [sortBy]: sortOrder })
      .skip(skip)
      .limit(limit)
      .toArray();
      
    return result;
  }
  
  /**
   * 获取所有分析数据
   * @param {Object} options 查询选项
   * @returns {Promise<Array>} 分析数据数组
   */
  static async getAllAnalysisData(options = {}) {
    const { limit = 50, skip = 0, sortBy = 'analysisTime', sortOrder = -1 } = options;
    
    const analysisCollection = await getAnalysisDataCollection();
    
    const result = await analysisCollection
      .find({})
      .sort({ [sortBy]: sortOrder })
      .skip(skip)
      .limit(limit)
      .toArray();
      
    return result;
  }
  
  /**
   * 根据ID获取分析数据
   * @param {string} id 分析数据ID
   * @returns {Promise<Object|null>} 分析数据对象或null
   */
  static async getAnalysisById(id) {
    const analysisCollection = await getAnalysisDataCollection();
    
    try {
      const objectId = new ObjectId(id);
      return await analysisCollection.findOne({ _id: objectId });
    } catch (error) {
      console.error('Invalid ObjectId format:', error);
      return null;
    }
  }
  
  /**
   * 删除分析数据
   * @param {string} id 分析数据ID
   * @param {string} userId 用户ID（用于权限验证）
   * @returns {Promise<boolean>} 删除是否成功
   */
  static async deleteAnalysis(id, userId) {
    const analysisCollection = await getAnalysisDataCollection();
    
    try {
      const objectId = new ObjectId(id);
      
      // 确保只有数据所有者或管理员可以删除
      const result = await analysisCollection.deleteOne({ 
        _id: objectId,
        userId: userId
      });
      
      return result.deletedCount > 0;
    } catch (error) {
      console.error('删除分析数据失败:', error);
      return false;
    }
  }
}

module.exports = AnalysisDataModel; 