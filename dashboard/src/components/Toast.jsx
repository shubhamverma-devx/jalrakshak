/**
 * Toast — neeche beech mein chhota confirmation.
 * mockup_v2.html ka `.toast`.
 *
 * KYUN alert()/confirm() nahi: browser ka dialog poora page rok deta hai aur design se
 * bilkul alag dikhta hai. Officer ko alert bhejne ke baad rukna nahi chahiye — usse
 * turant agla gaon dekhna hai. Toast dikhta hai aur khud chala jaata hai.
 *
 * INPUT: toast = { text, type } | null
 */

import { IconAlertTriangle, IconCheck } from '@tabler/icons-react'

export default function Toast({ toast }) {
  const isError = toast?.type === 'error'

  return (
    <div className={`toast${toast ? ' show' : ''}${isError ? ' error' : ''}`}>
      {isError ? <IconAlertTriangle className="ti" /> : <IconCheck className="ti" />}
      {/* Text hamesha render karte hain (khaali string bhi) taaki fade-out ke waqt
          text achanak gayab na ho — sirf opacity badle. */}
      {toast?.text || ''}
    </div>
  )
}
