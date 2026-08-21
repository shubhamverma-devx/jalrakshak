/**
 * Panel — har box ka common frame (header + body). mockup ka `.panel` + `.phead` + `.pbody`.
 *
 * KYUN ek shared component: dashboard mein 6 panel hain. Har jagah wahi header markup
 * copy karne se ek jagah padding badalne pe baaki 5 alag dikhne lagte.
 *
 * INPUT: title, Icon, right (header ke right side ka kuch bhi), bodyStyle, style, children
 */
export default function Panel({ title, Icon, right, children, style, bodyStyle, bodyClass }) {
  return (
    <div className="panel" style={style}>
      <div className="phead">
        <span className="ptitle">
          {Icon && <Icon className="ti" />}
          {title}
        </span>
        {right}
      </div>
      <div className={`pbody${bodyClass ? ' ' + bodyClass : ''}`} style={bodyStyle}>
        {children}
      </div>
    </div>
  )
}

/**
 * Empty — jab data hai hi nahi (koi SOS nahi, koi alert nahi).
 * KYUN: khaali panel "load ho raha hai" jaisa lagta hai. Saaf likhna behtar hai ki
 * kuch hai hi nahi — aur flood dashboard mein "koi SOS nahi" achhi khabar hai.
 */
export function Empty({ Icon, children }) {
  return (
    <div className="empty">
      {Icon && <Icon className="ti" />}
      <span>{children}</span>
    </div>
  )
}
