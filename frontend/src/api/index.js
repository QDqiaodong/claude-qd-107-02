import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 10000 })

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err?.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export const roomApi = {
  list: (params) => http.get('/rooms', { params }),
  create: (data) => http.post('/rooms', data),
  update: (id, data) => http.put(`/rooms/${id}`, data)
}

export const archiveApi = {
  list: (params) => http.get('/archives', { params }),
  create: (data) => http.post('/archives', data),
  update: (id, data) => http.put(`/archives/${id}`, data)
}

export const retrievalApi = {
  list: (params) => http.get('/retrievals', { params }),
  create: (data) => http.post('/retrievals', data),
  giveBack: (id, returnDate) =>
    http.post(`/retrievals/${id}/giveback`, null, { params: { returnDate } })
}

export const checkApi = {
  list: (params) => http.get('/checks', { params }),
  create: (data) => http.post('/checks', data)
}

export const appraisalApi = {
  pending: () => http.get('/appraisals/pending'),
  list: (params) => http.get('/appraisals', { params }),
  open: (archiveId) => http.post('/appraisals', null, { params: { archiveId } }),
  submit: (id, data) => http.post(`/appraisals/${id}/submit`, data)
}

export default http
