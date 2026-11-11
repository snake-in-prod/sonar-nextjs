import mongoose from 'mongoose';

export const Note = mongoose.model('Note', new mongoose.Schema({ text: String }));
