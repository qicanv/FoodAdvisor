import request from './request'

export const getRestaurants = () => {
  return request.get('/api/restaurants')
}