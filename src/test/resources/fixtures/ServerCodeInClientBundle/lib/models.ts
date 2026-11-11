import mongoose from 'mongoose';

export interface IUser {
  name: string;
}

export const User = mongoose.model('User', new mongoose.Schema({ name: String }));
