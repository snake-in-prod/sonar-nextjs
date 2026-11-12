import { auth } from './auth';

export default auth // Noncompliant {{matcher and authorized callback leave out 1 route that checks nothing themselves (/account)}}