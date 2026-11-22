const revealItems = document.querySelectorAll('.reveal')

if (revealItems.length === 0) {
  // nothing to animate
} else if ('IntersectionObserver' in window) {
  for (const item of revealItems) {
    item.classList.add('reveal--pending')
  }

  const observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.remove('reveal--pending')
          entry.target.classList.add('is-visible')
          observer.unobserve(entry.target)
        }
      }
    },
    { threshold: 0.2, rootMargin: '0px 0px -8% 0px' },
  )

  for (const item of revealItems) {
    observer.observe(item)
  }
}
