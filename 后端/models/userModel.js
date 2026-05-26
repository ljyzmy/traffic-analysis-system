const { getUsersCollection } = require('../db');
const bcrypt = require('bcrypt');

/**
 * 用户模型类
 */
class UserModel {
  /**
   * 注册新用户
   * @param {Object} userData 用户数据
   * @returns {Promise<Object>} 创建的用户对象
   */
  static async registerUser(userData) {
    const usersCollection = await getUsersCollection();
    
    // 检查用户名和邮箱是否已存在
    const existingUser = await usersCollection.findOne({
      $or: [
        { username: userData.username },
        { email: userData.email }
      ]
    });
    
    if (existingUser) {
      if (existingUser.username === userData.username) {
        throw new Error('用户名已存在');
      }
      if (existingUser.email === userData.email) {
        throw new Error('邮箱已被注册');
      }
    }
    
    // 对密码进行加密
    const saltRounds = 10;
    const hashedPassword = await bcrypt.hash(userData.password, saltRounds);
    
    // 创建新用户
    const newUser = {
      username: userData.username,
      password: hashedPassword,
      email: userData.email,
      role: userData.role || '普通用户', // 默认为普通用户
      createdAt: new Date()
    };
    
    const result = await usersCollection.insertOne(newUser);
    return { ...newUser, _id: result.insertedId };
  }
  
  /**
   * 验证用户登录
   * @param {string} username 用户名
   * @param {string} password 密码
   * @returns {Promise<Object|null>} 用户对象或null
   */
  static async validateUser(username, password) {
    const usersCollection = await getUsersCollection();
    
    // 查找用户
    const user = await usersCollection.findOne({ username });
    if (!user) {
      return null;
    }
    
    // 验证密码
    const isPasswordValid = await bcrypt.compare(password, user.password);
    if (!isPasswordValid) {
      return null;
    }
    
    // 不返回密码
    const { password: _, ...userWithoutPassword } = user;
    return userWithoutPassword;
  }
  
  /**
   * 通过ID获取用户
   * @param {string} userId 用户ID
   * @returns {Promise<Object|null>} 用户对象或null
   */
  static async getUserById(userId) {
    const usersCollection = await getUsersCollection();
    const user = await usersCollection.findOne({ _id: userId });
    
    if (!user) {
      return null;
    }
    
    // 不返回密码
    const { password: _, ...userWithoutPassword } = user;
    return userWithoutPassword;
  }
}

module.exports = UserModel; 